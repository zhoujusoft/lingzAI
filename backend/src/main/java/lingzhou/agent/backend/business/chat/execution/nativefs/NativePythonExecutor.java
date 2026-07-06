package lingzhou.agent.backend.business.chat.execution.nativefs;

import com.alibaba.fastjson.JSON;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionRequest;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnv;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnvManager;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnvResolver;
import lingzhou.agent.backend.business.chat.execution.python.PythonVenvPathSupport;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class NativePythonExecutor {

    private final LogicalPathResolver logicalPathResolver;
    private final RuntimeExecutionProperties properties;
    private final PythonRuntimeEnvResolver pythonRuntimeEnvResolver;
    private final PythonRuntimeEnvManager pythonRuntimeEnvManager;

    public NativePythonExecutor(
            LogicalPathResolver logicalPathResolver,
            RuntimeExecutionProperties properties,
            PythonRuntimeEnvResolver pythonRuntimeEnvResolver,
            PythonRuntimeEnvManager pythonRuntimeEnvManager) {
        this.logicalPathResolver = logicalPathResolver;
        this.properties = properties;
        this.pythonRuntimeEnvResolver = pythonRuntimeEnvResolver;
        this.pythonRuntimeEnvManager = pythonRuntimeEnvManager;
    }

    public RuntimeExecutionResult execute(PathJail jail, RuntimeExecutionRequest request) {
        String scriptPath = stringValue(request.payload(), "scriptPath");
        String workDir = stringValue(request.payload(), "workDir");
        int timeoutSeconds = intValue(request.payload(), "timeoutSeconds", properties.getCommandTimeoutSeconds(), 600);
        timeoutSeconds = Math.max(timeoutSeconds, properties.getCommandTimeoutSeconds());
        if (!StringUtils.hasText(scriptPath)) {
            return RuntimeExecutionResult.failure(request.action(), "RUN_PYTHON_SCRIPT_EMPTY", "scriptPath 不能为空");
        }
        try {
            String normalizedScriptPath = logicalPathResolver.normalizeLogicalPath(scriptPath);
            if (!(normalizedScriptPath.startsWith("/skill/scripts/")
                    || normalizedScriptPath.startsWith("/workspace/"))) {
                return RuntimeExecutionResult.failure(
                        request.action(),
                        "RUN_PYTHON_SCRIPT_PATH_INVALID",
                        "scriptPath 仅允许位于 /skill/scripts 或 /workspace 下");
            }
            Path resolvedScriptPath =
                    jail.assertReadable(logicalPathResolver.resolve(request.workspace(), normalizedScriptPath));
            if (!Files.isRegularFile(resolvedScriptPath)) {
                return RuntimeExecutionResult.failure(
                        request.action(), "RUN_PYTHON_SCRIPT_NOT_FOUND", "脚本不存在: " + normalizedScriptPath);
            }
            String resolvedWorkDirLogical =
                    StringUtils.hasText(workDir) ? workDir : request.workspace().defaultLogicalWorkDir();
            Path resolvedWorkDir = jail.assertReadable(logicalPathResolver.resolve(
                    request.workspace(), logicalPathResolver.normalizeLogicalPath(resolvedWorkDirLogical)));
            if (!Files.isDirectory(resolvedWorkDir)) {
                return RuntimeExecutionResult.failure(
                        request.action(), "RUN_PYTHON_WORKDIR_INVALID", "workDir 不是目录: " + resolvedWorkDirLogical);
            }

            PythonRuntimeEnv pythonEnv = pythonRuntimeEnvManager.ensureReady(pythonRuntimeEnvResolver.resolve(request));
            log.debug(
                    "[Python执行] 环境已命中：sessionId={}, scriptPath={}, envName={}, dedicated={}, envRoot={}, pythonPath={}",
                    request.sessionId(),
                    normalizedScriptPath,
                    pythonEnv.envName(),
                    pythonEnv.dedicated(),
                    pythonEnv.envRoot(),
                    pythonEnv.pythonPath());
            List<String> args = parseArgs(
                    request.payload() == null ? null : request.payload().get("args"));
            List<String> resolvedArgs = translateArgs(args, request.workspace());

            List<String> command = new ArrayList<>();
            command.add(pythonEnv.pythonPath().toString());
            command.add(resolvedScriptPath.toString());
            command.addAll(resolvedArgs);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(resolvedWorkDir.toFile());
            processBuilder.redirectErrorStream(true);
            applyRuntimeEnvironment(processBuilder.environment(), request, pythonEnv);

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            Thread reader = startReader(process, output);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.interrupt();
                return RuntimeExecutionResult.failure(
                        request.action(),
                        "RUN_PYTHON_TIMEOUT",
                        "Python 脚本执行超时（" + timeoutSeconds + "秒）\n" + truncate(output.toString()));
            }
            reader.join(1000);
            int exitCode = process.exitValue();
            String resultOutput = truncate(output.toString());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("scriptPath", normalizedScriptPath);
            data.put("resolvedScriptPath", resolvedScriptPath.toString());
            data.put("args", args);
            data.put("resolvedArgs", resolvedArgs);
            data.put("workDir", resolvedWorkDirLogical);
            data.put("resolvedWorkDir", resolvedWorkDir.toString());
            data.put("pythonEnvName", pythonEnv.envName());
            data.put("pythonPath", pythonEnv.pythonPath().toString());
            data.put("pythonEnvRoot", pythonEnv.envRoot().toString());
            data.put("exitCode", exitCode);
            data.put("timedOut", false);
            data.put("output", resultOutput);
            if (exitCode != 0) {
                return new RuntimeExecutionResult(
                        false,
                        request.action(),
                        resultOutput,
                        data,
                        "RUN_PYTHON_EXIT_NON_ZERO",
                        "Python 脚本执行失败，退出码: " + exitCode);
            }
            return RuntimeExecutionResult.success(request.action(), resultOutput, data);
        } catch (SandboxViolationException ex) {
            return RuntimeExecutionResult.failure(request.action(), "RUN_PYTHON_SANDBOX_BLOCKED", ex.getMessage());
        } catch (Exception ex) {
            return RuntimeExecutionResult.failure(request.action(), "RUN_PYTHON_EXECUTION_FAILED", ex.getMessage());
        }
    }

    private List<String> parseArgs(Object rawArgs) {
        if (rawArgs == null) {
            return List.of();
        }
        if (rawArgs instanceof List<?> rawList) {
            List<String> args = new ArrayList<>();
            for (Object item : rawList) {
                if (item != null) {
                    args.add(String.valueOf(item));
                }
            }
            return List.copyOf(args);
        }
        if (rawArgs instanceof String text && StringUtils.hasText(text)) {
            String trimmed = text.trim();
            if (trimmed.startsWith("[")) {
                try {
                    List<Object> parsed = JSON.parseArray(trimmed, Object.class);
                    if (parsed == null || parsed.isEmpty()) {
                        return List.of();
                    }
                    List<String> args = new ArrayList<>();
                    for (Object item : parsed) {
                        if (item != null) {
                            args.add(String.valueOf(item));
                        }
                    }
                    return List.copyOf(args);
                } catch (Exception ignored) {
                    // ignore and fall through
                }
            }
            return List.of(trimmed);
        }
        return List.of(String.valueOf(rawArgs));
    }

    private List<String> translateArgs(
            List<String> args, lingzhou.agent.backend.business.chat.execution.model.RuntimeWorkspace workspace) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }
        List<String> translated = new ArrayList<>(args.size());
        for (String arg : args) {
            if (!StringUtils.hasText(arg)) {
                translated.add(arg);
                continue;
            }
            String trimmed = arg.trim();
            if (!trimmed.startsWith("/")) {
                translated.add(arg);
                continue;
            }
            try {
                translated.add(logicalPathResolver
                        .resolve(workspace, trimmed)
                        .toAbsolutePath()
                        .normalize()
                        .toString());
            } catch (Exception ignored) {
                translated.add(arg);
            }
        }
        return List.copyOf(translated);
    }

    private void applyRuntimeEnvironment(
            Map<String, String> env, RuntimeExecutionRequest request, PythonRuntimeEnv pythonEnv) {
        env.put("HOME", request.workspace().workspaceRoot());
        env.put("USERPROFILE", request.workspace().workspaceRoot());
        env.put("TMPDIR", request.workspace().tempRoot());
        env.put("TMP", request.workspace().tempRoot());
        env.put("TEMP", request.workspace().tempRoot());
        env.put("PYTHONUNBUFFERED", "1");
        env.put("PYTHONNOUSERSITE", "1");
        env.put("LINGZ_SESSION_ID", request.sessionId() == null ? "" : request.sessionId());
        env.put("LINGZ_USER_ID", request.userId() == null ? "" : String.valueOf(request.userId()));
        env.put("LINGZ_SKILL_NAME", request.runtimeSkillName() == null ? "" : request.runtimeSkillName());
        env.put("LINGZ_WORKSPACE", request.workspace().workspaceRoot());
        env.put("LINGZ_UPLOADS", request.workspace().uploadsRoot());
        env.put("LINGZ_OUTPUTS", request.workspace().outputsRoot());
        env.put("LINGZ_TEMP", request.workspace().tempRoot());
        env.put("LINGZ_SKILL_DIR", request.workspace().skillRoot());
        if (pythonEnv != null) {
            String originalPath = env.getOrDefault("PATH", "");
            String preferredPythonBin = PythonVenvPathSupport.resolveBinDirectory(pythonEnv.venvPath())
                    .toString();
            env.put("PATH", preferredPythonBin + File.pathSeparator + originalPath);
            env.put("VIRTUAL_ENV", pythonEnv.venvPath().toString());
            env.put("LINGZ_PYTHON_ENV", pythonEnv.envRoot().toString());
        }
    }

    private Thread startReader(Process process, StringBuilder output) {
        Thread thread = new Thread(
                () -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append('\n');
                            if (output.length() > properties.getMaxBashOutputChars() + 2000) {
                                break;
                            }
                        }
                    } catch (IOException ignored) {
                        // ignore
                    }
                },
                "lingz-runtime-python-reader");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= properties.getMaxBashOutputChars()) {
            return text;
        }
        return text.substring(0, properties.getMaxBashOutputChars()) + "\n[output truncated]";
    }

    private int intValue(Map<String, Object> payload, String key, int defaultValue, int maxValue) {
        if (payload == null) {
            return defaultValue;
        }
        Object value = payload.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            if (parsed <= 0) {
                return defaultValue;
            }
            return Math.min(parsed, maxValue);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String stringValue(Map<String, Object> payload, String key) {
        if (payload == null || !StringUtils.hasText(key)) {
            return "";
        }
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
