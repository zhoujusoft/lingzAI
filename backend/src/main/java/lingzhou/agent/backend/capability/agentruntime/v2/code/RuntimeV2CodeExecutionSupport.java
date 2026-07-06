package lingzhou.agent.backend.capability.agentruntime.v2.code;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodePlanProtocol.CodeExecutionPlan;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2CodeExecutionSupport {

    public CodeExecutionPlan buildSuggestedPlan(
            String userRequest, String fileListJson, List<Map<String, Object>> observationTrace) {
        List<String> inputPaths = resolveSuggestedInputPaths(fileListJson, observationTrace);
        RuntimeV2CodeOutputHeuristics.OutputPreference outputPreference =
                RuntimeV2CodeOutputHeuristics.resolveOutputPreference(inputPaths, userRequest, userRequest);
        String outputFileName = RuntimeV2CodeOutputHeuristics.ensureFileNameExtension(
                "", outputPreference.extension(), "runtime_v2_output");
        return new CodeExecutionPlan(
                "/workspace/runtime_v2_task.py",
                inputPaths,
                "/outputs/" + outputFileName,
                outputFileName,
                outputPreference.mimeType(),
                "/workspace",
                600,
                normalizeText(userRequest));
    }

    public String buildAttachmentSummary(String fileListJson) {
        List<Map<String, Object>> files = parseFileList(fileListJson);
        if (files.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> file : files) {
            String name = normalizeText(file.get("name"));
            String path = normalizeLogicalUploadPath(normalizeText(file.get("path")), name);
            if (!StringUtils.hasText(name) && !StringUtils.hasText(path)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ").append(StringUtils.hasText(name) ? name : path);
            if (StringUtils.hasText(path)) {
                builder.append(" (").append(path).append(")");
            }
        }
        return builder.toString();
    }

    public List<String> resolveSuggestedInputPaths(RuntimeV2State state) {
        String fileListJson = state == null || state.prepared() == null
                ? null
                : state.prepared().fileListJson();
        List<Map<String, Object>> observationTrace = state == null ? List.of() : state.observationTrace();
        return resolveSuggestedInputPaths(fileListJson, observationTrace);
    }

    public List<String> resolveSuggestedInputPaths(String fileListJson, List<Map<String, Object>> observationTrace) {
        List<String> paths = new ArrayList<>();
        String lastParsedPath = resolveLastParsedPath(observationTrace);
        if (StringUtils.hasText(lastParsedPath)) {
            paths.add(lastParsedPath);
        }
        for (Map<String, Object> file : parseFileList(fileListJson)) {
            String name = normalizeText(file.get("name"));
            String path = normalizeLogicalUploadPath(normalizeText(file.get("path")), name);
            if (StringUtils.hasText(path) && !paths.contains(path)) {
                paths.add(path);
            }
        }
        return List.copyOf(paths);
    }

    public boolean isToolExecutionSuccess(String toolResult) {
        if (!StringUtils.hasText(toolResult)) {
            return false;
        }
        Map<String, Object> payload = tryParseJsonObject(toolResult);
        if (payload.isEmpty()) {
            return false;
        }
        Object success = payload.get("success");
        return success != null
                && "true".equalsIgnoreCase(String.valueOf(success).trim());
    }

    public Map<String, Object> buildCodeState(
            CodeExecutionPlan plan, String scriptContent, String attachmentSummary, String status) {
        Map<String, Object> codeState = new LinkedHashMap<>();
        codeState.put("status", normalizeText(status));
        codeState.put("attachmentSummary", normalizeText(attachmentSummary));
        codeState.put("plan", plan == null ? Map.of() : plan.toPromptPayload());
        codeState.put("scriptContent", normalizeText(scriptContent));
        if (plan != null) {
            codeState.put("suggestedInputPaths", plan.inputPaths());
            codeState.put("suggestedScriptPath", plan.scriptPath());
            codeState.put("suggestedOutputPath", plan.outputPath());
            codeState.put("outputFileName", plan.outputFileName());
            codeState.put("outputMimeType", plan.outputMimeType());
        }
        return Map.copyOf(codeState);
    }

    public CodeExecutionPlan readPlan(Map<String, Object> codeState, String fallbackGoal) {
        if (codeState == null || codeState.isEmpty()) {
            return null;
        }
        Object rawPlan = codeState.get("plan");
        if (!(rawPlan instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> planPayload = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            planPayload.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        String scriptPath = normalizeText(planPayload.get("scriptPath"));
        List<String> inputPaths = normalizeStringList(planPayload.get("inputPaths"));
        String outputPath = normalizeText(planPayload.get("outputPath"));
        String outputFileName = normalizeText(planPayload.get("outputFileName"));
        String outputMimeType = normalizeText(planPayload.get("outputMimeType"));
        String workDir = normalizeText(planPayload.get("workDir"));
        Integer timeoutSeconds = normalizeInteger(planPayload.get("timeoutSeconds"));
        String goal = normalizeText(planPayload.get("goal"));
        if (!StringUtils.hasText(goal)) {
            goal = normalizeText(fallbackGoal);
        }
        if (!StringUtils.hasText(scriptPath)
                || inputPaths.isEmpty()
                || !StringUtils.hasText(outputPath)
                || !StringUtils.hasText(outputFileName)
                || !StringUtils.hasText(outputMimeType)
                || !StringUtils.hasText(workDir)
                || timeoutSeconds == null) {
            return null;
        }
        return new CodeExecutionPlan(
                scriptPath, inputPaths, outputPath, outputFileName, outputMimeType, workDir, timeoutSeconds, goal);
    }

    public String readScriptContent(Map<String, Object> codeState) {
        if (codeState == null || codeState.isEmpty()) {
            return "";
        }
        return normalizeText(codeState.get("scriptContent"));
    }

    public String readCodeStatus(Map<String, Object> codeState) {
        if (codeState == null || codeState.isEmpty()) {
            return "";
        }
        return normalizeText(codeState.get("status"));
    }

    public Map<String, Object> buildFileWriteArguments(CodeExecutionPlan plan, String scriptContent) {
        if (plan == null) {
            return Map.of();
        }
        return Map.of("path", plan.scriptPath(), "content", normalizeText(scriptContent));
    }

    public Map<String, Object> buildRunPythonArguments(CodeExecutionPlan plan) {
        if (plan == null) {
            return Map.of();
        }
        List<String> args = new ArrayList<>(plan.inputPaths());
        args.add(plan.outputPath());
        return Map.of(
                "scriptPath", plan.scriptPath(),
                "args", List.copyOf(args),
                "workDir", plan.workDir(),
                "timeoutSeconds", plan.timeoutSeconds());
    }

    public Map<String, Object> buildWriteArtifactArguments(CodeExecutionPlan plan) {
        if (plan == null) {
            return Map.of();
        }
        return Map.of(
                "folder", "runtime_v2",
                "fileName", plan.outputFileName(),
                "content", "",
                "sourcePath", plan.outputPath(),
                "contentType", plan.outputMimeType());
    }

    public String buildCodeFileWriteObservation(
            CodeExecutionPlan plan, String toolResultPreview, boolean writeSuccess, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "codeStage", "file-write");
        appendObservationLine(
                builder,
                "status",
                writeSuccess ? RuntimeV2CodeState.CODE_SCRIPT_READY : RuntimeV2CodeState.CODE_SCRIPT_WRITE_FAILED);
        appendObservationLine(builder, "scriptPath", plan.scriptPath());
        appendObservationLine(builder, "inputPaths", JSON.toJSONString(plan.inputPaths()));
        appendObservationLine(builder, "outputPath", plan.outputPath());
        appendObservationLine(builder, "outputFileName", plan.outputFileName());
        appendObservationLine(builder, "outputMimeType", plan.outputMimeType());
        appendObservationLine(builder, "workDir", plan.workDir());
        appendObservationLine(builder, "timeoutSeconds", String.valueOf(plan.timeoutSeconds()));
        appendObservationLine(builder, "goal", normalizeText(plan.goal()));
        appendObservationLine(builder, "writeSuccess", String.valueOf(writeSuccess));
        appendObservationLine(builder, "stageOutcome", writeSuccess ? "script-ready" : "script-write-failed");
        appendObservationLine(builder, "outputReady", "false");
        appendObservationLine(builder, "artifactPublished", "false");
        if (!writeSuccess) {
            appendObservationLine(builder, "failureKind", resolveCodeObservationFailureKind(toolResultPreview));
        }
        if (StringUtils.hasText(toolResultPreview)) {
            appendObservationLine(builder, "toolResult", toolResultPreview);
        }
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    public String buildCodeRunObservation(
            CodeExecutionPlan plan, String toolResultPreview, boolean runSuccess, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "codeStage", "run-python");
        appendObservationLine(
                builder,
                "status",
                runSuccess ? RuntimeV2CodeState.CODE_OUTPUT_READY : RuntimeV2CodeState.CODE_RUN_FAILED);
        appendObservationLine(builder, "scriptPath", plan.scriptPath());
        appendObservationLine(builder, "inputPaths", JSON.toJSONString(plan.inputPaths()));
        appendObservationLine(builder, "outputPath", plan.outputPath());
        appendObservationLine(builder, "outputFileName", plan.outputFileName());
        appendObservationLine(builder, "outputMimeType", plan.outputMimeType());
        appendObservationLine(builder, "workDir", plan.workDir());
        appendObservationLine(builder, "timeoutSeconds", String.valueOf(plan.timeoutSeconds()));
        appendObservationLine(builder, "goal", normalizeText(plan.goal()));
        appendObservationLine(builder, "runSuccess", String.valueOf(runSuccess));
        appendObservationLine(builder, "stageOutcome", runSuccess ? "output-ready" : "run-failed");
        appendObservationLine(builder, "outputReady", String.valueOf(runSuccess));
        appendObservationLine(builder, "artifactPublished", "false");
        if (!runSuccess) {
            appendObservationLine(builder, "failureKind", resolveCodeObservationFailureKind(toolResultPreview));
        }
        if (StringUtils.hasText(toolResultPreview)) {
            appendObservationLine(builder, "toolResult", toolResultPreview);
        }
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    public String buildCodeArtifactObservation(
            CodeExecutionPlan plan, String toolResultPreview, boolean publishSuccess, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "codeStage", "write-artifact");
        appendObservationLine(
                builder,
                "status",
                publishSuccess
                        ? RuntimeV2CodeState.CODE_ARTIFACT_READY
                        : RuntimeV2CodeState.CODE_ARTIFACT_WRITE_FAILED);
        appendObservationLine(builder, "scriptPath", plan.scriptPath());
        appendObservationLine(builder, "inputPaths", JSON.toJSONString(plan.inputPaths()));
        appendObservationLine(builder, "outputPath", plan.outputPath());
        appendObservationLine(builder, "outputFileName", plan.outputFileName());
        appendObservationLine(builder, "outputMimeType", plan.outputMimeType());
        appendObservationLine(builder, "goal", normalizeText(plan.goal()));
        appendObservationLine(builder, "publishSuccess", String.valueOf(publishSuccess));
        appendObservationLine(builder, "stageOutcome", publishSuccess ? "artifact-ready" : "artifact-write-failed");
        appendObservationLine(builder, "outputReady", "true");
        appendObservationLine(builder, "artifactPublished", String.valueOf(publishSuccess));
        if (!publishSuccess) {
            appendObservationLine(builder, "failureKind", resolveCodeObservationFailureKind(toolResultPreview));
        }
        if (StringUtils.hasText(toolResultPreview)) {
            appendObservationLine(builder, "toolResult", toolResultPreview);
        }
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    private String resolveCodeObservationFailureKind(String toolResultPreview) {
        String preview = normalizeText(toolResultPreview);
        if (!StringUtils.hasText(preview)) {
            return "";
        }
        String explicitFailureKind = extractObservationField(preview, "failureKind");
        if (StringUtils.hasText(explicitFailureKind)) {
            return explicitFailureKind;
        }
        String errorCode = extractObservationField(preview, "errorCode");
        if ("FILE_WRITE_PYTHON_BLOCKED".equalsIgnoreCase(errorCode)) {
            return "python-blocked";
        }
        if ("RUN_PYTHON_EXIT_NON_ZERO".equalsIgnoreCase(errorCode)) {
            return "run-python-failed";
        }
        return "";
    }

    private String resolveLastParsedPath(List<Map<String, Object>> observationTrace) {
        if (observationTrace == null || observationTrace.isEmpty()) {
            return "";
        }
        for (int index = observationTrace.size() - 1; index >= 0; index -= 1) {
            Map<String, Object> item = observationTrace.get(index);
            if (!"parse_file".equalsIgnoreCase(normalizeText(item.get("toolName")))) {
                continue;
            }
            Map<String, Object> arguments = tryParseJsonObject(normalizeText(item.get("arguments")));
            String path = normalizeText(arguments.get("arg0"));
            if (!StringUtils.hasText(path)) {
                path = normalizeText(arguments.get("path"));
            }
            return normalizeLogicalUploadPath(path, path);
        }
        return "";
    }

    private List<Map<String, Object>> parseFileList(String fileListJson) {
        if (!StringUtils.hasText(fileListJson)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> parsed =
                    JSON.parseObject(fileListJson, new TypeReference<List<Map<String, Object>>>() {});
            return parsed == null ? List.of() : parsed;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeLogicalUploadPath(String path, String fallbackName) {
        String normalizedPath = normalizeText(path);
        if (StringUtils.hasText(normalizedPath)) {
            if (normalizedPath.contains("chat-upload://")) {
                String normalizedName = normalizeText(fallbackName);
                if (StringUtils.hasText(normalizedName) && !normalizedName.contains("chat-upload://")) {
                    return "/uploads/" + normalizedName;
                }
                return "";
            }
            if (normalizedPath.startsWith("/")) {
                return normalizedPath;
            }
            return "/uploads/" + normalizedPath;
        }
        String normalizedName = normalizeText(fallbackName);
        if (!StringUtils.hasText(normalizedName)) {
            return "";
        }
        return "/uploads/" + normalizedName;
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

    private void appendObservationLine(StringBuilder builder, String key, String value) {
        if (builder == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(key).append(": ").append(value.trim());
    }

    private String extractObservationField(String observation, String fieldName) {
        if (!StringUtils.hasText(observation) || !StringUtils.hasText(fieldName)) {
            return "";
        }
        String prefix = fieldName.trim() + ":";
        for (String line : observation.split("\\R")) {
            String normalizedLine = normalizeText(line);
            if (normalizedLine.startsWith(prefix)) {
                return normalizeText(normalizedLine.substring(prefix.length()));
            }
        }
        return "";
    }

    private String trimForPrompt(String text, int maxPromptLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        if (maxPromptLength <= 0 || normalized.length() <= maxPromptLength) {
            return normalized;
        }
        return normalized.substring(0, maxPromptLength) + "\n...[truncated]";
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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
}
