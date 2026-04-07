package lingzhou.agent.backend.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.util.StringUtils;

public final class SkillFilesystemSupport {

    private static final String SKILLS_DIR_NAME = "skills";

    private static volatile Path configuredSkillRoot;

    private SkillFilesystemSupport() {}

    static void configureRoot(String rootDir) {
        if (!StringUtils.hasText(rootDir)) {
            configuredSkillRoot = null;
            return;
        }
        configuredSkillRoot = resolveConfiguredSkillRoot(rootDir.trim());
    }

    public static Path resolveSkillRoot() {
        Path configured = configuredSkillRoot;
        if (configured != null) {
            return configured;
        }
        String configuredRoot = System.getProperty("skill.root");
        if (!StringUtils.hasText(configuredRoot)) {
            configuredRoot = System.getenv("SKILL_ROOT");
        }
        if (StringUtils.hasText(configuredRoot)) {
            return normalizeSkillRoot(Path.of(configuredRoot));
        }

        Path discovered = discoverSkillRoot(Path.of(System.getProperty("user.dir", ".")));
        if (discovered != null) {
            return discovered;
        }

        return Path.of(System.getProperty("user.dir", "."))
                .resolve(SKILLS_DIR_NAME)
                .toAbsolutePath()
                .normalize();
    }

    public static Path resolveSkillPath(String pathValue) {
        Path rawPath = Path.of(pathValue);
        if (rawPath.isAbsolute()) {
            return rawPath.toAbsolutePath().normalize();
        }

        String normalized = pathValue.replace('\\', '/');
        String relative = normalized.startsWith(SKILLS_DIR_NAME + "/")
                ? normalized.substring((SKILLS_DIR_NAME + "/").length())
                : normalized;
        return resolveSkillRoot().resolve(relative).toAbsolutePath().normalize();
    }

    public static String readSkillFile(String pathValue) throws IOException {
        return Files.readString(resolveSkillPath(pathValue), StandardCharsets.UTF_8);
    }

    private static Path resolveConfiguredSkillRoot(String configuredRootValue) {
        Path configuredRoot = Path.of(configuredRootValue);
        Path normalized = normalizeSkillRoot(configuredRoot);
        if (Files.isDirectory(normalized)) {
            return normalized;
        }

        Path discovered = discoverSkillRoot(Path.of(System.getProperty("user.dir", ".")));
        if (discovered != null) {
            return discovered;
        }

        return normalized;
    }

    private static Path normalizeSkillRoot(Path configuredRoot) {
        Path normalized = configuredRoot.toAbsolutePath().normalize();
        Path nestedSkillRoot = normalized.resolve(SKILLS_DIR_NAME);
        if (Files.isDirectory(nestedSkillRoot)) {
            return nestedSkillRoot;
        }
        return normalized;
    }

    private static Path discoverSkillRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(SKILLS_DIR_NAME);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }
}
