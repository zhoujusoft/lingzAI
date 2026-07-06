package lingzhou.agent.backend.business.chat.execution.tool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.RuntimeExecutionFacade;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionAction;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.execution.nativefs.PythonScriptWritePolicy;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RuntimeSystemToolProvider {

    private final RuntimeExecutionFacade runtimeExecutionFacade;
    private final ChatFileService chatFileService;

    public RuntimeSystemToolProvider(RuntimeExecutionFacade runtimeExecutionFacade, ChatFileService chatFileService) {
        this.runtimeExecutionFacade = runtimeExecutionFacade;
        this.chatFileService = chatFileService;
    }

    @Tool(
            name = "file_read",
            description =
                    "Read a UTF-8 text file from a logical runtime path such as /workspace, /outputs, /temp, /profile or a relative path inside the current runtime workspace. Do not use this for binary files like .xlsx, .docx or .pdf.")
    public String readFile(@ToolParam(description = "Logical or relative UTF-8 text file path") String path) {
        return format(RuntimeExecutionAction.FILE_READ, payload("path", path));
    }

    @Tool(
            name = "file_write",
            description =
                    "Write a small UTF-8 text file to a logical runtime path such as /workspace/... or a relative path inside the current runtime workspace. Do not put large datasets, complete HTML reports, or very large scripts into one call because tool arguments may be truncated; prefer existing render/artifact tools and minimal scripts."
                            + PythonScriptWritePolicy.FILE_WRITE_TOOL_DESCRIPTION_SUFFIX)
    public String writeFile(
            @ToolParam(description = "Logical or relative file path") String path,
            @ToolParam(description = "UTF-8 text content") String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", path);
        payload.put("content", content);
        return format(RuntimeExecutionAction.FILE_WRITE, payload);
    }

    @Tool(
            name = "list_dir",
            description =
                    "List entries under a logical runtime directory such as /workspace, /uploads, /outputs, /temp or a relative directory inside the current runtime workspace.")
    public String listDir(@ToolParam(description = "Logical or relative directory path") String path) {
        return format(RuntimeExecutionAction.LIST_DIR, payload("path", path));
    }

    @Tool(
            name = "stat",
            description =
                    "Inspect metadata of a logical runtime file or directory path, including existence, size and type.")
    public String stat(@ToolParam(description = "Logical or relative file path") String path) {
        return format(RuntimeExecutionAction.STAT, payload("path", path));
    }

    @Tool(
            name = "run_python",
            description =
                    "Execute a Python script that already exists under /workspace or /skill/scripts. Pass input and output logical paths through args instead of hardcoding them in the script body. Only use this after confirming existing tools are insufficient.")
    public String runPython(
            @ToolParam(
                            description =
                                    "Logical script path under /workspace; /skill/scripts is only valid in skill world")
                    String scriptPath,
            @ToolParam(
                            description =
                                    "Optional argument array or JSON array string. When passing input/output files, use logical paths such as /uploads/a.xlsx and /outputs/result.xlsx so the runtime can translate them for the script.")
                    List<String> args,
            @ToolParam(description = "Optional logical working directory, default /workspace") String workDir,
            @ToolParam(description = "Optional timeout in seconds, default 600, max 600") Integer timeoutSeconds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scriptPath", scriptPath);
        payload.put("args", args == null ? List.of() : List.copyOf(args));
        payload.put("workDir", workDir);
        payload.put("timeoutSeconds", timeoutSeconds);
        return format(RuntimeExecutionAction.RUN_PYTHON, payload);
    }

    @Tool(
            name = "write_artifact",
            description =
                    "Publish a final downloadable artifact. Prefer sourcePath when a script already generated the file. Use content only for UTF-8 text artifacts.")
    public String writeArtifact(
            @ToolParam(description = "Artifact category folder, e.g. translation") String folder,
            @ToolParam(description = "Final file name, e.g. translated-output.docx") String fileName,
            @ToolParam(
                            description =
                                    "UTF-8 text content. Leave empty when sourcePath is provided; never serialize binary document content here.")
                    String content,
            @ToolParam(
                            description =
                                    "Optional logical source file path under current runtime workspace. Existing files are copied into /outputs and uploaded as artifacts.")
                    String sourcePath,
            @ToolParam(description = "Optional MIME content type") String contentType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("folder", folder);
        payload.put("fileName", fileName);
        payload.put("content", content);
        payload.put("sourcePath", sourcePath);
        payload.put("contentType", contentType);
        return format(RuntimeExecutionAction.WRITE_ARTIFACT, payload);
    }

    private String format(RuntimeExecutionAction action, Map<String, Object> payload) {
        RuntimeToolContext context = RuntimeToolInvocationContextHolder.get();
        if (context == null) {
            return RuntimeToolResultFormatter.format(
                    RuntimeExecutionResult.failure(action, "RUNTIME_CONTEXT_EMPTY", "Runtime tool context is empty"));
        }
        Map<String, Object> normalizedPayload = normalizePayload(context, action, payload);
        RuntimeExecutionResult validationResult = validateInvocation(context, action, normalizedPayload);
        if (validationResult != null) {
            log.debug(
                    "[运行时工具] 前置校验拦截：会话ID={}, 动作={}, 错误码={}", context.sessionId(), action, validationResult.errorCode());
            return RuntimeToolResultFormatter.format(validationResult);
        }
        RuntimeExecutionResult result = runtimeExecutionFacade.execute(context, action, normalizedPayload);
        log.debug(
                "[运行时工具] 执行结果：会话ID={}, 动作={}, 成功={}, 错误码={}",
                context.sessionId(),
                action,
                result.success(),
                result.errorCode());
        return RuntimeToolResultFormatter.format(result);
    }

    private Map<String, Object> normalizePayload(
            RuntimeToolContext context, RuntimeExecutionAction action, Map<String, Object> payload) {
        if (context == null || action == null || payload == null || payload.isEmpty()) {
            return payload;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(payload);
        switch (action) {
            case FILE_READ, LIST_DIR, STAT -> rewritePathField(context, normalized, "path");
            case RUN_PYTHON -> rewriteRunPythonArgs(context, normalized);
            default -> {
                return normalized;
            }
        }
        return normalized;
    }

    private void rewritePathField(RuntimeToolContext context, Map<String, Object> payload, String key) {
        String originalPath = normalizeText(payload.get(key));
        if (!hasText(originalPath)) {
            return;
        }
        String canonicalPath = chatFileService.canonicalizeLogicalUploadPath(context.fileListJson(), originalPath);
        if (!originalPath.equals(canonicalPath)) {
            log.debug("[运行时工具] 附件路径已归一化：sessionId={}, from={}, to={}", context.sessionId(), originalPath, canonicalPath);
            payload.put(key, canonicalPath);
        }
    }

    private void rewriteRunPythonArgs(RuntimeToolContext context, Map<String, Object> payload) {
        Object rawArgs = payload.get("args");
        if (rawArgs instanceof List<?> rawList) {
            List<String> canonicalArgs = new ArrayList<>();
            for (Object item : rawList) {
                canonicalArgs.add(chatFileService.canonicalizeLogicalUploadPath(context.fileListJson(), normalizeText(item)));
            }
            payload.put("args", List.copyOf(canonicalArgs));
        }
    }

    private Map<String, Object> payload(String key, Object value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(key, value);
        return payload;
    }

    private RuntimeExecutionResult validateInvocation(
            RuntimeToolContext context, RuntimeExecutionAction action, Map<String, Object> payload) {
        if (context == null || action == null) {
            return null;
        }
        String skillPath = resolveSkillPath(action, payload);
        if (isSkillPath(skillPath) && shouldBlockSkillPathAccess(context)) {
            return RuntimeExecutionResult.failure(
                    action,
                    "RUNTIME_TOOL_SKILL_PATH_FORBIDDEN",
                    "当前个人 Agent 执行链路未激活可执行 skill，禁止直接访问 /skill 目录。请先命中并加载对应 skill。");
        }
        return null;
    }

    private boolean shouldBlockSkillPathAccess(RuntimeToolContext context) {
        if (!context.personalAgent()) {
            return false;
        }
        return "EXECUTION_TASK".equalsIgnoreCase(context.personalAgentMode())
                && !readPrecheckBoolean(context.paramsJson(), "allowSkillInternals");
    }

    private String resolveSkillPath(RuntimeExecutionAction action, Map<String, Object> payload) {
        if (payload == null || action == null) {
            return "";
        }
        return switch (action) {
            case RUN_PYTHON -> normalizeText(payload.get("scriptPath"));
            case FILE_READ, FILE_WRITE, LIST_DIR, STAT -> normalizeText(payload.get("path"));
            case WRITE_ARTIFACT -> normalizeText(payload.get("sourcePath"));
            default -> "";
        };
    }

    private boolean isSkillPath(String path) {
        return hasText(path) && normalizeText(path).startsWith("/skill");
    }

    private String readPrecheckField(String paramsJson, String key) {
        if (!hasText(paramsJson) || !hasText(key)) {
            return "";
        }
        try {
            Map<String, Object> payload = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            if (payload == null) {
                return "";
            }
            Object executionPrecheck = payload.get("executionPrecheck");
            if (!(executionPrecheck instanceof Map<?, ?> precheckMap)) {
                return "";
            }
            Object value = precheckMap.get(key);
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean readPrecheckBoolean(String paramsJson, String key) {
        String value = readPrecheckField(paramsJson, key);
        return "true".equalsIgnoreCase(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
