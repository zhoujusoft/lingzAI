package lingzhou.agent.backend.capability.agentruntime.v2.code;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionProtocol;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2CodePlanProtocol {

    private final RuntimeV2ReactDecisionProtocol reactDecisionProtocol;

    public RuntimeV2CodePlanProtocol(RuntimeV2ReactDecisionProtocol reactDecisionProtocol) {
        this.reactDecisionProtocol = reactDecisionProtocol;
    }

    public CodePlanValidation validate(String rawOutput, List<String> suggestedInputPaths, String fallbackGoal) {
        if (!StringUtils.hasText(rawOutput)) {
            return CodePlanValidation.invalid("CODE 计划输出为空");
        }
        Map<String, Object> payload;
        try {
            payload = JSON.parseObject(
                    reactDecisionProtocol.stripCodeFence(rawOutput), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return CodePlanValidation.invalid("CODE 计划不是合法 JSON");
        }
        if (payload == null || payload.isEmpty()) {
            return CodePlanValidation.invalid("CODE 计划 JSON 为空");
        }

        String scriptPath = normalizeText(payload.get("scriptPath"));
        if (!StringUtils.hasText(scriptPath)) {
            scriptPath = "/workspace/runtime_v2_task.py";
        }
        if (!scriptPath.startsWith("/workspace/")) {
            return CodePlanValidation.invalid("scriptPath 必须位于 /workspace");
        }

        List<String> inputPaths = normalizeStringList(payload.get("inputPaths"));
        if (inputPaths.isEmpty() && suggestedInputPaths != null && !suggestedInputPaths.isEmpty()) {
            inputPaths = List.copyOf(suggestedInputPaths);
        }
        if (inputPaths.isEmpty()) {
            return CodePlanValidation.invalid("inputPaths 不能为空");
        }

        String outputPath = normalizeText(payload.get("outputPath"));
        if (!StringUtils.hasText(outputPath)) {
            return CodePlanValidation.invalid("outputPath 不能为空");
        }
        if (!outputPath.startsWith("/outputs/")) {
            return CodePlanValidation.invalid("outputPath 必须位于 /outputs");
        }
        String outputFileName = normalizeText(payload.get("outputFileName"));
        if (!StringUtils.hasText(outputFileName)) {
            outputFileName = resolveFileName(outputPath);
        }
        if (!StringUtils.hasText(outputFileName)) {
            return CodePlanValidation.invalid("outputFileName 不能为空");
        }
        String outputMimeType = normalizeText(payload.get("outputMimeType"));
        if (!StringUtils.hasText(outputMimeType)) {
            outputMimeType = inferMimeType(outputFileName);
        }
        String workDir = normalizeText(payload.get("workDir"));
        if (!StringUtils.hasText(workDir)) {
            workDir = "/workspace";
        }
        Integer timeoutSeconds = normalizeInteger(payload.get("timeoutSeconds"));
        if (timeoutSeconds == null) {
            timeoutSeconds = 600;
        }
        timeoutSeconds = Math.max(30, Math.min(600, timeoutSeconds));
        String goal = normalizeText(payload.get("goal"));
        if (!StringUtils.hasText(goal)) {
            goal = normalizeText(fallbackGoal);
        }
        RuntimeV2CodeOutputHeuristics.OutputPreference outputPreference =
                RuntimeV2CodeOutputHeuristics.resolveOutputPreference(inputPaths, goal, fallbackGoal);
        if (outputPreference.explicitRequest()) {
            outputFileName = RuntimeV2CodeOutputHeuristics.ensureFileNameExtension(
                    outputFileName, outputPreference.extension(), "runtime_v2_output");
            outputMimeType = outputPreference.mimeType();
            outputPath = RuntimeV2CodeOutputHeuristics.ensureOutputPathExtension(
                    outputPath, outputFileName, outputPreference.extension());
        } else {
            if (!StringUtils.hasText(outputFileName)) {
                String fallbackExtension = outputPreference.extension();
                outputFileName = RuntimeV2CodeOutputHeuristics.ensureFileNameExtension(
                        outputFileName, fallbackExtension, "runtime_v2_output");
            }
            if (!StringUtils.hasText(outputMimeType)) {
                outputMimeType = inferMimeType(outputFileName);
            }
            if (!StringUtils.hasText(resolveFileName(outputPath))) {
                outputPath = RuntimeV2CodeOutputHeuristics.ensureOutputPathExtension(
                        outputPath,
                        outputFileName,
                        RuntimeV2CodeOutputHeuristics.resolveOutputPreference(inputPaths, goal, fallbackGoal)
                                .extension());
            }
        }
        return CodePlanValidation.valid(new CodeExecutionPlan(
                scriptPath,
                List.copyOf(inputPaths),
                outputPath,
                outputFileName,
                outputMimeType,
                workDir,
                timeoutSeconds,
                goal));
    }

    private List<String> normalizeStringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List<?> rawList) {
            for (Object item : rawList) {
                String normalized = normalizeText(item);
                if (StringUtils.hasText(normalized) && !values.contains(normalized)) {
                    values.add(normalized);
                }
            }
        } else if (value instanceof String text && StringUtils.hasText(text)) {
            String normalized = normalizeText(text);
            if (normalized.startsWith("[")) {
                try {
                    List<String> parsed = JSON.parseObject(normalized, new TypeReference<List<String>>() {});
                    if (parsed != null) {
                        for (String item : parsed) {
                            String candidate = normalizeText(item);
                            if (StringUtils.hasText(candidate) && !values.contains(candidate)) {
                                values.add(candidate);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    values.add(normalized);
                }
            } else {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private Integer normalizeInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveFileName(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex >= path.length() - 1) {
            return path.trim();
        }
        return path.substring(slashIndex + 1).trim();
    }

    private String inferMimeType(String fileName) {
        String normalized = normalizeText(fileName).toLowerCase();
        if (normalized.endsWith(".html") || normalized.endsWith(".htm")) {
            return "text/html";
        }
        if (normalized.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (normalized.endsWith(".csv")) {
            return "text/csv";
        }
        if (normalized.endsWith(".json")) {
            return "application/json";
        }
        if (normalized.endsWith(".zip")) {
            return "application/zip";
        }
        if (normalized.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record CodeExecutionPlan(
            String scriptPath,
            List<String> inputPaths,
            String outputPath,
            String outputFileName,
            String outputMimeType,
            String workDir,
            Integer timeoutSeconds,
            String goal) {

        public Map<String, Object> toPromptPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scriptPath", scriptPath);
            payload.put("inputPaths", inputPaths);
            payload.put("outputPath", outputPath);
            payload.put("outputFileName", outputFileName);
            payload.put("outputMimeType", outputMimeType);
            payload.put("workDir", workDir);
            payload.put("timeoutSeconds", timeoutSeconds);
            payload.put("goal", goal);
            return payload;
        }
    }

    public record CodePlanValidation(boolean valid, CodeExecutionPlan plan, String errorMessage) {

        public static CodePlanValidation valid(CodeExecutionPlan plan) {
            return new CodePlanValidation(true, plan, "");
        }

        public static CodePlanValidation invalid(String errorMessage) {
            return new CodePlanValidation(false, null, errorMessage == null ? "CODE 计划校验失败" : errorMessage);
        }
    }
}
