package lingzhou.agent.backend.business.chat.execution.nativefs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionRequest;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnv;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnvManager;
import lingzhou.agent.backend.business.chat.execution.python.PythonRuntimeEnvResolver;
import lingzhou.agent.backend.business.chat.execution.python.PythonVenvPathSupport;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NativeCommandExecutor {

    private final CommandPathTranslator commandPathTranslator;
    private final CommandSafetyPolicy commandSafetyPolicy;
    private final RuntimeExecutionProperties properties;
    private final PythonRuntimeEnvResolver pythonRuntimeEnvResolver;
    private final PythonRuntimeEnvManager pythonRuntimeEnvManager;

    public NativeCommandExecutor(
            CommandPathTranslator commandPathTranslator,
            CommandSafetyPolicy commandSafetyPolicy,
            RuntimeExecutionProperties properties,
            PythonRuntimeEnvResolver pythonRuntimeEnvResolver,
            PythonRuntimeEnvManager pythonRuntimeEnvManager) {
        this.commandPathTranslator = commandPathTranslator;
        this.commandSafetyPolicy = commandSafetyPolicy;
        this.properties = properties;
        this.pythonRuntimeEnvResolver = pythonRuntimeEnvResolver;
        this.pythonRuntimeEnvManager = pythonRuntimeEnvManager;
    }

    public RuntimeExecutionResult execute(PathJail jail, RuntimeExecutionRequest request) {
        String command = stringValue(request.payload(), "command");
        String workDir = stringValue(request.payload(), "workDir");
        int timeoutSeconds = intValue(request.payload(), "timeoutSeconds", properties.getCommandTimeoutSeconds(), 600);
        if (!StringUtils.hasText(command)) {
            return RuntimeExecutionResult.failure(request.action(), "BASH_COMMAND_EMPTY", "command 不能为空");
        }
        try {
            commandSafetyPolicy.assertSafe(command);
            String resolvedWorkDir = commandPathTranslator.resolveWorkDir(request.workspace(), workDir);
            Path workDirPath = jail.assertReadable(Path.of(resolvedWorkDir));
            if (!Files.isDirectory(workDirPath)) {
                return RuntimeExecutionResult.failure(
                        request.action(), "BASH_WORKDIR_INVALID", "workDir 不是目录: " + workDir);
            }
            String translatedCommand = commandPathTranslator.translateCommand(request.workspace(), command);
            ProcessBuilder processBuilder = buildProcess(translatedCommand, workDirPath);
            PythonRuntimeEnv pythonEnv = preparePythonEnvIfNeeded(command, request);
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
                        "BASH_TIMEOUT",
                        "命令执行超时（" + timeoutSeconds + "秒）\n" + truncate(output.toString()));
            }
            reader.join(1000);
            int exitCode = process.exitValue();
            String resultOutput = truncate(output.toString());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("command", command);
            data.put("translatedCommand", translatedCommand);
            data.put(
                    "workDir",
                    StringUtils.hasText(workDir) ? workDir : request.workspace().defaultLogicalWorkDir());
            data.put("resolvedWorkDir", workDirPath.toString());
            data.put("exitCode", exitCode);
            data.put("timedOut", false);
            data.put("output", resultOutput);
            if (exitCode != 0) {
                return new RuntimeExecutionResult(
                        false, request.action(), resultOutput, data, "BASH_EXIT_NON_ZERO", "命令执行失败，退出码: " + exitCode);
            }
            return RuntimeExecutionResult.success(request.action(), resultOutput, data);
        } catch (SandboxViolationException ex) {
            return RuntimeExecutionResult.failure(request.action(), "BASH_SANDBOX_BLOCKED", ex.getMessage());
        } catch (Exception ex) {
            return RuntimeExecutionResult.failure(request.action(), "BASH_EXECUTION_FAILED", ex.getMessage());
        }
    }

    private ProcessBuilder buildProcess(String command, Path workDir) {
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/zsh", "-lc", command);
        processBuilder.directory(workDir.toFile());
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

    private PythonRuntimeEnv preparePythonEnvIfNeeded(String command, RuntimeExecutionRequest request) {
        if (!pythonRuntimeEnvResolver.isPythonCommand(command)) {
            return null;
        }
        PythonRuntimeEnv resolvedEnv = pythonRuntimeEnvResolver.resolve(request);
        return pythonRuntimeEnvManager.ensureReady(resolvedEnv);
    }

    private void applyRuntimeEnvironment(
            Map<String, String> env, RuntimeExecutionRequest request, PythonRuntimeEnv pythonEnv) {
        env.put("HOME", request.workspace().workspaceRoot());
        env.put("USERPROFILE", request.workspace().workspaceRoot());
        env.put("TMPDIR", request.workspace().tempRoot());
        env.put("TMP", request.workspace().tempRoot());
        env.put("TEMP", request.workspace().tempRoot());
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
            String defaultPythonBin = Path.of(properties.getWorkspaceBaseDir())
                    .toAbsolutePath()
                    .normalize()
                    .resolve("public")
                    .resolve("runtime-envs")
                    .resolve("python")
                    .resolve("default")
                    .resolve(".venv")
                    .resolve(PythonVenvPathSupport.isWindows() ? "Scripts" : "bin")
                    .toString();
            String preferredPythonBin = PythonVenvPathSupport.resolveBinDirectory(pythonEnv.venvPath())
                    .toString();
            env.put(
                    "PATH",
                    preferredPythonBin + File.pathSeparator + defaultPythonBin + File.pathSeparator + originalPath);
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
                "lingz-runtime-bash-reader");
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
        if (payload == null || key == null || key.isBlank()) {
            return "";
        }
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
