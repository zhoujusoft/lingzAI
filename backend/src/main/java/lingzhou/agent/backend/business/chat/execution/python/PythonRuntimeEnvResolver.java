package lingzhou.agent.backend.business.chat.execution.python;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionRequest;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PythonRuntimeEnvResolver {

    private final RuntimeExecutionProperties properties;

    public PythonRuntimeEnvResolver(RuntimeExecutionProperties properties) {
        this.properties = properties;
    }

    public boolean isPythonCommand(String command) {
        if (!StringUtils.hasText(command)) {
            return false;
        }
        String normalized = command.trim();
        return normalized.matches(".*(?:^|\\s)(python|python\\d+(?:\\.\\d+)?|pip|pip\\d+(?:\\.\\d+)?|uv)(?:\\s|$).*");
    }

    public PythonRuntimeEnv resolve(RuntimeExecutionRequest request) {
        String skillName = request.runtimeSkillName() == null
                ? ""
                : request.runtimeSkillName().trim();
        Long userId = request.userId();
        String scriptPath =
                request.payload() == null ? "" : trimText(request.payload().get("scriptPath"));
        Path skillRequirements = StringUtils.hasText(request.workspace().skillRoot())
                ? Path.of(request.workspace().skillRoot())
                        .resolve("requirements.txt")
                        .normalize()
                : null;
        Path skillRoot = StringUtils.hasText(request.workspace().skillRoot())
                ? Path.of(request.workspace().skillRoot()).toAbsolutePath().normalize()
                : null;
        return resolve(userId, skillName, scriptPath, skillRoot, skillRequirements);
    }

    public PythonRuntimeEnv resolve(String skillName, Path skillRoot) {
        return resolve(null, skillName, "", skillRoot);
    }

    public PythonRuntimeEnv resolve(String skillName, String scriptPath, Path skillRoot) {
        return resolve(null, skillName, scriptPath, skillRoot);
    }

    public PythonRuntimeEnv resolve(Long userId, String skillName, String scriptPath, Path skillRoot) {
        Path normalizedSkillRoot =
                skillRoot == null ? null : skillRoot.toAbsolutePath().normalize();
        Path skillRequirements = normalizedSkillRoot == null
                ? null
                : normalizedSkillRoot.resolve("requirements.txt").normalize();
        return resolve(userId, skillName, scriptPath, normalizedSkillRoot, skillRequirements);
    }

    private PythonRuntimeEnv resolve(
            Long userId, String skillName, String scriptPath, Path skillRoot, Path skillRequirements) {
        Path runtimePythonRoot = Path.of(properties.getWorkspaceBaseDir())
                .toAbsolutePath()
                .normalize()
                .resolve("public")
                .resolve("runtime-envs")
                .resolve("python");
        Path defaultRoot = runtimePythonRoot.resolve("default");
        Path generalCodeSharedRoot = runtimePythonRoot.resolve("general-code");
        if (isWorkspaceScript(scriptPath)) {
            long safeUserId = userId == null || userId <= 0 ? 0L : userId;
            Path generalCodeUserRoot = Path.of(properties.getWorkspaceBaseDir())
                    .toAbsolutePath()
                    .normalize()
                    .resolve("users")
                    .resolve(String.valueOf(safeUserId))
                    .resolve("runtime-envs")
                    .resolve("python")
                    .resolve("general-code");
            Path vendorDir = resolveEnvVendorDir(generalCodeSharedRoot);
            String vendorSha256 = hashVendorDir(vendorDir);
            Path venvPath = generalCodeUserRoot.resolve(".venv");
            Path generalRequirements = generalCodeSharedRoot.resolve("requirements.txt");
            String sha256 = Files.isRegularFile(generalRequirements) ? sha256(generalRequirements) : "";
            return new PythonRuntimeEnv(
                    "general-code:user-" + safeUserId,
                    "general-code",
                    false,
                    generalCodeUserRoot,
                    venvPath,
                    PythonVenvPathSupport.resolvePythonExecutable(venvPath),
                    Files.isRegularFile(generalRequirements) ? generalRequirements : null,
                    sha256,
                    vendorDir,
                    vendorSha256,
                    generalCodeUserRoot.resolve("manifest.json"),
                    generalCodeUserRoot.resolve("install.log"));
        }
        if (!StringUtils.hasText(skillName) || skillRequirements == null || !Files.isRegularFile(skillRequirements)) {
            Path vendorDir = resolveEnvVendorDir(defaultRoot);
            String vendorSha256 = hashVendorDir(vendorDir);
            Path venvPath = defaultRoot.resolve(".venv");
            Path defaultRequirements = defaultRoot.resolve("requirements.txt");
            String sha256 = Files.isRegularFile(defaultRequirements) ? sha256(defaultRequirements) : "";
            return new PythonRuntimeEnv(
                    "default",
                    skillName,
                    false,
                    defaultRoot,
                    venvPath,
                    PythonVenvPathSupport.resolvePythonExecutable(venvPath),
                    Files.isRegularFile(defaultRequirements) ? defaultRequirements : null,
                    sha256,
                    vendorDir,
                    vendorSha256,
                    defaultRoot.resolve("manifest.json"),
                    defaultRoot.resolve("install.log"));
        }

        String sha256 = sha256(skillRequirements);
        Path vendorDir = resolveVendorDir(skillRoot);
        String vendorSha256 = hashVendorDir(vendorDir);
        Path envRoot = runtimePythonRoot.resolve("skills").resolve(skillName);
        Path venvPath = envRoot.resolve(".venv");
        return new PythonRuntimeEnv(
                "skill:" + skillName,
                skillName,
                true,
                envRoot,
                venvPath,
                PythonVenvPathSupport.resolvePythonExecutable(venvPath),
                skillRequirements,
                sha256,
                vendorDir,
                vendorSha256,
                envRoot.resolve("manifest.json"),
                envRoot.resolve("install.log"));
    }

    private Path resolveVendorDir(Path skillRoot) {
        if (skillRoot == null) {
            return null;
        }
        Path vendor = skillRoot.resolve("vendor").normalize();
        return Files.isDirectory(vendor) ? vendor : null;
    }

    private Path resolveEnvVendorDir(Path envRoot) {
        if (envRoot == null) {
            return null;
        }
        Path vendor = envRoot.resolve("vendor").normalize();
        return Files.isDirectory(vendor) ? vendor : null;
    }

    private String sha256(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("计算 requirements 指纹失败: " + file, ex);
        }
    }

    private String hashVendorDir(Path vendorDir) {
        if (vendorDir == null || !Files.isDirectory(vendorDir)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.walk(vendorDir)) {
                stream.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(
                                path -> vendorDir.relativize(path).toString()))
                        .forEach(path -> updateDigest(digest, vendorDir, path));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("计算 vendor 指纹失败: " + vendorDir, ex);
        }
    }

    private void updateDigest(MessageDigest digest, Path vendorDir, Path file) {
        try {
            digest.update(vendorDir.relativize(file).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(Files.readAllBytes(file));
        } catch (Exception ex) {
            throw new IllegalStateException("读取 vendor 文件失败: " + file, ex);
        }
    }

    private boolean isWorkspaceScript(String scriptPath) {
        return StringUtils.hasText(scriptPath) && scriptPath.trim().startsWith("/workspace/");
    }

    private String trimText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
