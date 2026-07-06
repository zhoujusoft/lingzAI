package lingzhou.agent.backend.business.chat.execution.python;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PythonRuntimeEnvDescriptorService {

    private static final int MAX_REQUIREMENT_LINES = 12;

    private final RuntimeExecutionProperties properties;

    public PythonRuntimeEnvDescriptorService(RuntimeExecutionProperties properties) {
        this.properties = properties;
    }

    public String buildGeneralCodePrompt() {
        Path sharedRoot = resolvePythonRoot().resolve("general-code");
        Path requirementsPath = sharedRoot.resolve("requirements.txt");
        Path vendorDir = sharedRoot.resolve("vendor");
        List<String> requirements = readRequirements(requirementsPath);

        StringBuilder builder = new StringBuilder();
        builder.append("## CODE Python 环境\n");
        builder.append("- 当前通用 CODE 环境：`general-code`\n");
        builder.append("- 共享依赖定义目录：`")
                .append(sharedRoot.toAbsolutePath().normalize())
                .append("`\n");
        builder.append("- 离线依赖包目录：`")
                .append(vendorDir.toAbsolutePath().normalize())
                .append("`\n");
        builder.append(
                "- `.venv`、`manifest.json`、`install.log` 按用户隔离，路径位于：`<workspaceBase>/users/<userId>/runtime-envs/python/general-code/`\n");
        builder.append("- 首次执行 `/workspace/*.py` 时，会按当前用户构建自己的 `general-code` 环境，不与其他用户共享 `.venv`\n");
        if (requirements.isEmpty()) {
            builder.append("- 预装依赖：当前未声明\n");
        } else {
            builder.append("- 预装依赖（写代码时优先直接使用，不要猜版本）：\n");
            for (String requirement : requirements) {
                builder.append("  - `").append(requirement).append("`\n");
            }
        }
        builder.append("- 若以上依赖已能满足任务，不要重复安装或改用其他包。\n");
        return builder.toString();
    }

    private Path resolvePythonRoot() {
        return Path.of(properties.getWorkspaceBaseDir())
                .toAbsolutePath()
                .normalize()
                .resolve("public")
                .resolve("runtime-envs")
                .resolve("python");
    }

    private List<String> readRequirements(Path requirementsPath) {
        if (requirementsPath == null || !Files.isRegularFile(requirementsPath)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(requirementsPath, StandardCharsets.UTF_8);
            List<String> result = new ArrayList<>();
            for (String line : lines) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                String normalized = line.trim();
                if (normalized.startsWith("#")) {
                    continue;
                }
                result.add(normalized);
                if (result.size() >= MAX_REQUIREMENT_LINES) {
                    break;
                }
            }
            return List.copyOf(result);
        } catch (IOException ignored) {
            return List.of();
        }
    }
}
