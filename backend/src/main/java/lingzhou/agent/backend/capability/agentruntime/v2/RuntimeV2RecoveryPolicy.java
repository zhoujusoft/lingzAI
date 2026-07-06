package lingzhou.agent.backend.capability.agentruntime.v2;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2RecoveryPolicy {

    private static final int MAX_SCRIPT_WRITE_RETRY_COUNT = 1;
    private static final int MAX_RUN_RETRY_COUNT = 2;

    public boolean shouldRetryCodeScriptWrite(Object toolResult, int retryCount) {
        if (retryCount >= MAX_SCRIPT_WRITE_RETRY_COUNT) {
            return false;
        }
        Map<String, Object> payload = parseToolResult(toolResult);
        if (payload.isEmpty()) {
            return false;
        }
        return "FILE_WRITE_PYTHON_BLOCKED".equalsIgnoreCase(normalizeText(payload.get("errorCode")));
    }

    public boolean shouldRetryCodeRun(Object toolResult, int retryCount) {
        if (retryCount >= MAX_RUN_RETRY_COUNT) {
            return false;
        }
        return isRecoverableRunPythonFailure(toolResult);
    }

    public boolean shouldRetryCodeRun(Object toolResult, List<Map<String, Object>> observationTrace) {
        if (countRecoverableRunPythonFailures(observationTrace) >= MAX_RUN_RETRY_COUNT) {
            return false;
        }
        return isRecoverableRunPythonFailure(toolResult);
    }

    public int countRecoverableRunPythonFailures(List<Map<String, Object>> observationTrace) {
        if (observationTrace == null || observationTrace.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> item : observationTrace) {
            if (!"run_python".equalsIgnoreCase(normalizeText(item.get("toolName")))) {
                continue;
            }
            String observation = normalizeText(item.get("observation"));
            String failureKind = extractObservationField(observation, "failureKind");
            if ("no-matching-pdf-found".equalsIgnoreCase(failureKind)) {
                count += 1;
                continue;
            }
            String lowered = observation.toLowerCase(Locale.ROOT);
            if (lowered.contains("errorcode: run_python_exit_non_zero")
                    && isRecoverableZipPdfExtractionFailure(lowered)) {
                count += 1;
            }
        }
        return count;
    }

    public String resolveRecoverableFailureProgressMessage(String toolName, Object toolResult) {
        if (!StringUtils.hasText(toolName) || toolResult == null) {
            return "";
        }
        Map<String, Object> payload = parseToolResult(toolResult);
        if (payload.isEmpty()) {
            return "";
        }
        String success = normalizeText(payload.get("success"));
        if (!"false".equalsIgnoreCase(success)) {
            return "";
        }
        String action = normalizeText(payload.get("action"));
        if (!toolName.endsWith(".execute_dataset_sql") && !"EXECUTE_DATASET_SQL".equalsIgnoreCase(action)) {
            return "";
        }
        String unknownColumnName = normalizeText(payload.get("unknownColumnName"));
        String nextActionHint = normalizeText(payload.get("nextActionHint"));
        if (StringUtils.hasText(unknownColumnName)) {
            return "SQL 查询失败，字段 `" + unknownColumnName + "` 不存在，正在根据返回的结构提示重规划查询。";
        }
        if (StringUtils.hasText(nextActionHint)) {
            return "SQL 查询失败，正在根据返回的结构提示重规划查询。";
        }
        return "";
    }

    private boolean isRecoverableRunPythonFailure(Object toolResult) {
        Map<String, Object> payload = parseToolResult(toolResult);
        if (payload.isEmpty()) {
            return false;
        }
        if (!"RUN_PYTHON_EXIT_NON_ZERO".equalsIgnoreCase(normalizeText(payload.get("errorCode")))) {
            return false;
        }
        Map<String, Object> data = asObject(payload.get("data"));
        String textOutput = firstNonBlank(normalizeText(payload.get("textOutput")), normalizeText(data.get("output")));
        return isRecoverableZipPdfExtractionFailure(textOutput);
    }

    private boolean isRecoverableZipPdfExtractionFailure(String text) {
        String normalized = normalizeText(text).toLowerCase(Locale.ROOT);
        return normalized.contains("no invoice pdf files found")
                || normalized.contains("no pdf files found in the archive")
                || normalized.contains("no pdf files found");
    }

    private Map<String, Object> parseToolResult(Object toolResult) {
        if (toolResult instanceof Map<?, ?> rawMap) {
            return asObject(rawMap);
        }
        String text = normalizeText(toolResult);
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(text, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private String extractObservationField(String observation, String fieldName) {
        if (!StringUtils.hasText(observation) || !StringUtils.hasText(fieldName)) {
            return "";
        }
        String prefix = fieldName.trim() + ":";
        for (String line : observation.split("\\R")) {
            String normalized = normalizeText(line);
            if (normalized.startsWith(prefix)) {
                return normalizeText(normalized.substring(prefix.length()));
            }
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
