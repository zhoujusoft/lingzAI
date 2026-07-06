package lingzhou.agent.backend.capability.agentruntime.v2.observation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2ReadOnlyToolGuard {

    private static final Set<String> READ_ONLY_RUNTIME_TOOLS = Set.of("file_read", "parse_file", "list_dir", "stat");

    public String buildDuplicateObservation(
            RuntimeV2State state, String toolName, Map<String, Object> arguments, int maxPromptLength) {
        return buildDuplicateObservation(
                state == null ? List.of() : state.observationTrace(), toolName, arguments, maxPromptLength);
    }

    public String buildDuplicateObservation(
            List<Map<String, Object>> observationTrace,
            String toolName,
            Map<String, Object> arguments,
            int maxPromptLength) {
        if (!isReadOnlyRuntimeTool(toolName)) {
            return "";
        }
        String signature = buildToolCallSignature(toolName, arguments);
        if (!StringUtils.hasText(signature) || !hasPriorToolObservation(observationTrace, signature)) {
            return "";
        }
        return RuntimeV2GuardObservationFactory.buildDuplicateReadOnlyObservation(toolName, signature, maxPromptLength);
    }

    public boolean isDuplicateReadOnlyTool(
            List<Map<String, Object>> observationTrace, String toolName, Map<String, Object> arguments) {
        if (!isReadOnlyRuntimeTool(toolName)) {
            return false;
        }
        String signature = buildToolCallSignature(toolName, arguments);
        return StringUtils.hasText(signature) && hasPriorToolObservation(observationTrace, signature);
    }

    private boolean hasPriorToolObservation(List<Map<String, Object>> observationTrace, String signature) {
        if (observationTrace == null || !StringUtils.hasText(signature) || observationTrace.isEmpty()) {
            return false;
        }
        for (Map<String, Object> item : observationTrace) {
            String toolName = normalizeText(item.get("toolName"));
            Map<String, Object> args = tryParseJsonObject(normalizeText(item.get("arguments")));
            String existingSignature = buildToolCallSignature(toolName, args);
            if (signature.equals(existingSignature)) {
                return true;
            }
        }
        return false;
    }

    public String buildToolCallSignature(String toolName, Map<String, Object> arguments) {
        String normalizedToolName = normalizeText(toolName);
        if (!StringUtils.hasText(normalizedToolName)) {
            return "";
        }
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        if ("file_read".equalsIgnoreCase(normalizedToolName) || "stat".equalsIgnoreCase(normalizedToolName)) {
            String path = normalizeToolPathArgument(safeArguments);
            return normalizedToolName + "::" + path;
        }
        if ("list_dir".equalsIgnoreCase(normalizedToolName)) {
            String path = normalizeToolPathArgument(safeArguments);
            return normalizedToolName + "::" + path;
        }
        if ("parse_file".equalsIgnoreCase(normalizedToolName)) {
            String path = normalizeToolPathArgument(safeArguments);
            String mode = normalizeText(safeArguments.get("arg1"));
            if (!StringUtils.hasText(mode)) {
                mode = normalizeText(safeArguments.get("mode"));
            }
            return normalizedToolName + "::" + path + "::" + mode;
        }
        return normalizedToolName + "::" + JSON.toJSONString(safeArguments);
    }

    private String normalizeToolPathArgument(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        String path = normalizeText(arguments.get("arg0"));
        if (!StringUtils.hasText(path)) {
            path = normalizeText(arguments.get("path"));
        }
        return path.replace('\\', '/');
    }

    public boolean isReadOnlyRuntimeTool(String toolName) {
        return READ_ONLY_RUNTIME_TOOLS.contains(normalizeText(toolName).toLowerCase());
    }

    private Map<String, Object> tryParseJsonObject(String text) {
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

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
