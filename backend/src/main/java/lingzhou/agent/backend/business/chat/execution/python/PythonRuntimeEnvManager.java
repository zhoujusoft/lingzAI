package lingzhou.agent.backend.business.chat.execution.python;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PythonRuntimeEnvManager {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String INSTALL_MODE_DEFAULT = "DEFAULT";
    private static final String INSTALL_MODE_ONLINE = "ONLINE";
    private static final String INSTALL_MODE_VENDOR = "VENDOR";
    private static final String INSTALL_MODE_VENDOR_FALLBACK_ONLINE = "VENDOR_FALLBACK_ONLINE";
    private static final String INSTALL_POLICY_ISOLATED_VENV = "ISOLATED_VENV_V2";
    private final ConcurrentHashMap<String, ReentrantLock> installLocks = new ConcurrentHashMap<>();

    private final RuntimeExecutionProperties properties;
    private final ObjectMapper objectMapper;

    public PythonRuntimeEnvManager(RuntimeExecutionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public PythonRuntimeEnv ensureReady(PythonRuntimeEnv env) {
        if (env == null) {
            throw new IllegalArgumentException("PythonRuntimeEnv 不能为空");
        }
        ReentrantLock lock = installLocks.computeIfAbsent(lockKey(env), key -> new ReentrantLock());
        lock.lock();
        try {
            try {
                Files.createDirectories(env.envRoot());
                if (isReusable(env)) {
                    return env;
                }
                install(env);
                return env;
            } catch (IOException ex) {
                throw new IllegalStateException("初始化 Python 环境失败: " + env.envRoot(), ex);
            }
        } finally {
            lock.unlock();
        }
    }

    public EnvStatus readStatus(PythonRuntimeEnv env) {
        if (env == null) {
            throw new IllegalArgumentException("PythonRuntimeEnv 不能为空");
        }
        PythonRuntimeEnvManifest manifest =
                Files.isRegularFile(env.manifestPath()) ? readManifest(env.manifestPath()) : null;
        boolean pythonReady = Files.isRegularFile(env.pythonPath());
        boolean venvExists = Files.isDirectory(env.venvPath());
        boolean reusable = false;
        try {
            reusable = isReusable(env);
        } catch (IOException ignored) {
            // ignore
        }
        return new EnvStatus(
                env.skillName(),
                env.dedicated(),
                env.envRoot().toString(),
                env.venvPath().toString(),
                env.pythonPath().toString(),
                env.requirementsPath() == null ? "" : env.requirementsPath().toString(),
                env.requirementsSha256(),
                env.vendorDir() == null ? "" : env.vendorDir().toString(),
                env.vendorSha256(),
                env.manifestPath().toString(),
                env.installLogPath().toString(),
                pythonReady,
                venvExists,
                reusable,
                manifest);
    }

    public PythonRuntimeEnv rebuild(PythonRuntimeEnv env) {
        if (env == null) {
            throw new IllegalArgumentException("PythonRuntimeEnv 不能为空");
        }
        ReentrantLock lock = installLocks.computeIfAbsent(lockKey(env), key -> new ReentrantLock());
        lock.lock();
        try {
            deleteIfExists(env.venvPath());
            deleteIfExists(env.manifestPath());
            deleteIfExists(env.installLogPath());
            deleteIfExists(env.envRoot().resolve("requirements.source.txt"));
            deleteIfExists(env.envRoot().resolve("requirements.lock.txt"));
            return ensureReady(env);
        } finally {
            lock.unlock();
        }
    }

    private boolean isReusable(PythonRuntimeEnv env) throws IOException {
        if (!Files.isRegularFile(env.pythonPath()) || !Files.isRegularFile(env.manifestPath())) {
            return false;
        }
        PythonRuntimeEnvManifest manifest = readManifest(env.manifestPath());
        if (manifest == null || !STATUS_SUCCESS.equalsIgnoreCase(manifest.installStatus())) {
            return false;
        }
        if (!INSTALL_POLICY_ISOLATED_VENV.equalsIgnoreCase(nullToEmpty(manifest.installPolicy()))) {
            return false;
        }
        String envPythonVersion = readPythonVersion(env.pythonPath());
        if (!StringUtils.hasText(envPythonVersion)) {
            return false;
        }
        if (StringUtils.hasText(manifest.pythonVersion())
                && !envPythonVersion.equalsIgnoreCase(manifest.pythonVersion().trim())) {
            return false;
        }
        String platformPythonVersion = readPythonVersion(properties.getPythonCommand());
        if (StringUtils.hasText(platformPythonVersion)
                && !envPythonVersion.equalsIgnoreCase(platformPythonVersion.trim())) {
            return false;
        }
        if (!StringUtils.hasText(env.requirementsSha256())) {
            return !StringUtils.hasText(env.vendorSha256())
                    || env.vendorSha256().equalsIgnoreCase(manifest.vendorSha256());
        }
        if (!env.requirementsSha256().equalsIgnoreCase(manifest.requirementsSha256())) {
            return false;
        }
        if (!StringUtils.hasText(env.vendorSha256())) {
            return !StringUtils.hasText(manifest.vendorSha256());
        }
        return env.vendorSha256().equalsIgnoreCase(manifest.vendorSha256());
    }

    private void install(PythonRuntimeEnv env) throws IOException {
        Files.createDirectories(env.envRoot());
        if (env.requirementsPath() != null && Files.isRegularFile(env.requirementsPath())) {
            Files.writeString(
                    env.envRoot().resolve("requirements.source.txt"),
                    Files.readString(env.requirementsPath(), StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
            Files.writeString(
                    env.envRoot().resolve("requirements.lock.txt"),
                    Files.readString(env.requirementsPath(), StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
        }
        StringBuilder logBuilder = new StringBuilder();
        String installMode = INSTALL_MODE_DEFAULT;
        try {
            runCommand(
                    List.of(
                            properties.getPythonCommand(),
                            "-m",
                            "venv",
                            env.venvPath().toString()),
                    env.envRoot(),
                    false,
                    logBuilder);
            if (env.requirementsPath() != null && Files.isRegularFile(env.requirementsPath())) {
                Path sourceRequirements = env.envRoot().resolve("requirements.source.txt");
                installMode = installRequirements(env, sourceRequirements, logBuilder);
            }
            Files.writeString(env.installLogPath(), logBuilder.toString(), StandardCharsets.UTF_8);
            writeManifest(env, STATUS_SUCCESS, installMode);
        } catch (Exception ex) {
            logBuilder.append("\n[install failed] ").append(ex.getMessage()).append('\n');
            Files.writeString(env.installLogPath(), logBuilder.toString(), StandardCharsets.UTF_8);
            writeManifest(env, STATUS_FAILED, installMode);
            throw new IllegalStateException("构建 Python 环境失败: " + env.envRoot(), ex);
        }
    }

    private String installRequirements(PythonRuntimeEnv env, Path sourceRequirements, StringBuilder logBuilder)
            throws IOException, InterruptedException {
        String pipIndexUrl = normalizePipIndexUrl(properties.getPipIndexUrl());
        if (env.vendorDir() != null && Files.isDirectory(env.vendorDir()) && hasVendorArtifacts(env.vendorDir())) {
            try {
                logBuilder.append("\n[vendor install] 优先尝试离线 vendor 安装\n");
                runCommand(
                        List.of(
                                env.pythonPath().toString(),
                                "-m",
                                "pip",
                                "install",
                                "--cache-dir",
                                resolveCacheDir("pip").toString(),
                                "--no-index",
                                "--find-links",
                                env.vendorDir().toString(),
                                "-r",
                                sourceRequirements.toString()),
                        env.envRoot(),
                        false,
                        logBuilder);
                return INSTALL_MODE_VENDOR;
            } catch (IllegalStateException ex) {
                logBuilder
                        .append("\n[vendor install fallback] vendor 不完整或不匹配，回退在线安装：")
                        .append(ex.getMessage())
                        .append('\n');
                runCommand(
                        List.of(
                                env.pythonPath().toString(),
                                "-m",
                                "pip",
                                "install",
                                "--cache-dir",
                                resolveCacheDir("pip").toString(),
                                "--find-links",
                                env.vendorDir().toString(),
                                "-i",
                                pipIndexUrl,
                                "-r",
                                sourceRequirements.toString()),
                        env.envRoot(),
                        true,
                        logBuilder);
                return INSTALL_MODE_VENDOR_FALLBACK_ONLINE;
            }
        }
        runCommand(
                        List.of(
                                env.pythonPath().toString(),
                                "-m",
                                "pip",
                                "install",
                                "--cache-dir",
                                resolveCacheDir("pip").toString(),
                                "-i",
                                pipIndexUrl,
                                "-r",
                                sourceRequirements.toString()),
                env.envRoot(),
                true,
                logBuilder);
        return INSTALL_MODE_ONLINE;
    }

    private void runCommand(List<String> command, Path workDir, boolean allowOnlineIndex, StringBuilder logBuilder)
            throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workDir.toFile());
        processBuilder.redirectErrorStream(true);
        Map<String, String> env = processBuilder.environment();
        env.put("PIP_CACHE_DIR", resolveCacheDir("pip").toString());
        env.put("PYTHONNOUSERSITE", "1");
        if (allowOnlineIndex) {
            env.put("PIP_NO_INDEX", "0");
        } else {
            env.put("PIP_NO_INDEX", "1");
        }
        applyPipMirrorEnv(env);
        Process process = processBuilder.start();
        try (InputStream inputStream = process.getInputStream()) {
            logBuilder.append(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("命令执行失败: " + String.join(" ", command) + " (exit=" + exitCode + ")");
        }
    }

    private void applyPipMirrorEnv(Map<String, String> env) {
        if (env == null || !StringUtils.hasText(properties.getPipIndexUrl())) {
            return;
        }
        env.put("PIP_INDEX_URL", properties.getPipIndexUrl().trim());
    }

    private String normalizePipIndexUrl(String pipIndexUrl) {
        if (!StringUtils.hasText(pipIndexUrl)) {
            return "https://pypi.tuna.tsinghua.edu.cn/simple";
        }
        return pipIndexUrl.trim();
    }

    private void writeManifest(PythonRuntimeEnv env, String status, String installMode) throws IOException {
        String pythonVersion = "";
        if (Files.isRegularFile(env.pythonPath())) {
            pythonVersion = readPythonVersion(env.pythonPath());
        }
        PythonRuntimeEnvManifest manifest = new PythonRuntimeEnvManifest(
                env.skillName(),
                pythonVersion,
                env.requirementsSha256(),
                env.requirementsPath() == null ? "" : env.requirementsPath().toString(),
                env.vendorDir() == null ? "" : env.vendorDir().toString(),
                env.vendorSha256(),
                env.venvPath().toString(),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).toString(),
                status,
                StringUtils.hasText(installMode) ? installMode : INSTALL_MODE_DEFAULT,
                INSTALL_POLICY_ISOLATED_VENV);
        Files.writeString(
                env.manifestPath(),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest),
                StandardCharsets.UTF_8);
    }

    private PythonRuntimeEnvManifest readManifest(Path manifestPath) {
        try {
            return objectMapper.readValue(manifestPath.toFile(), PythonRuntimeEnvManifest.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String readPythonVersion(Path pythonPath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(pythonPath.toString(), "--version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            process.waitFor();
            return output;
        } catch (Exception ex) {
            return "";
        }
    }

    private String readPythonVersion(String pythonCommand) {
        if (!StringUtils.hasText(pythonCommand)) {
            return "";
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(pythonCommand.trim(), "--version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            process.waitFor();
            return output;
        } catch (Exception ex) {
            return "";
        }
    }

    private Path resolveCacheDir(String key) throws IOException {
        Path cacheDir = Path.of(properties.getWorkspaceBaseDir())
                .toAbsolutePath()
                .normalize()
                .resolve("public")
                .resolve("runtime-envs")
                .resolve("caches")
                .resolve(key);
        Files.createDirectories(cacheDir);
        return cacheDir;
    }

    private boolean hasVendorArtifacts(Path vendorDir) throws IOException {
        if (vendorDir == null || !Files.isDirectory(vendorDir)) {
            return false;
        }
        try (var stream = Files.list(vendorDir)) {
            return stream.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName() != null
                    && path.getFileName().toString().toLowerCase().endsWith(".whl"));
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.walk(path)) {
                    stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(target -> {
                        try {
                            Files.deleteIfExists(target);
                        } catch (IOException ex) {
                            throw new IllegalStateException("删除路径失败: " + target, ex);
                        }
                    });
                }
                return;
            }
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("删除路径失败: " + path, ex);
        }
    }

    private String lockKey(PythonRuntimeEnv env) {
        return env.envRoot().toAbsolutePath().normalize().toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public record EnvStatus(
            String skillName,
            boolean dedicated,
            String envRoot,
            String venvPath,
            String pythonPath,
            String requirementsPath,
            String requirementsSha256,
            String vendorPath,
            String vendorSha256,
            String manifestPath,
            String installLogPath,
            boolean pythonReady,
            boolean venvExists,
            boolean reusable,
            PythonRuntimeEnvManifest manifest) {}
}
