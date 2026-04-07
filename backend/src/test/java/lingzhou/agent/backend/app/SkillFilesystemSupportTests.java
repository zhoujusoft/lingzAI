package lingzhou.agent.backend.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillFilesystemSupportTests {

    @TempDir
    Path tempDir;

    @Test
    void resolveSkillRootUsesConfiguredRepoRoot() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Path skillRoot = repoRoot.resolve("skills");
        Files.createDirectories(skillRoot);

        String previousSkillRoot = System.getProperty("skill.root");
        try {
            System.setProperty("skill.root", repoRoot.toString());
            assertThat(SkillFilesystemSupport.resolveSkillRoot()).isEqualTo(skillRoot);
        } finally {
            restoreSystemProperty("skill.root", previousSkillRoot);
        }
    }

    @Test
    void resolveSkillRootDiscoversNearestSkillsDirectoryFromUserDir() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Path skillRoot = repoRoot.resolve("skills");
        Path nestedWorkingDir = repoRoot.resolve("backend/target/test-work");
        Files.createDirectories(skillRoot);
        Files.createDirectories(nestedWorkingDir);

        String previousSkillRoot = System.getProperty("skill.root");
        String previousUserDir = System.getProperty("user.dir");
        try {
            System.clearProperty("skill.root");
            System.setProperty("user.dir", nestedWorkingDir.toString());

            assertThat(SkillFilesystemSupport.resolveSkillRoot()).isEqualTo(skillRoot);
            assertThat(SkillFilesystemSupport.resolveSkillPath("skills/pdf-extractor/SKILL.md"))
                    .isEqualTo(skillRoot.resolve("pdf-extractor/SKILL.md"));
        } finally {
            restoreSystemProperty("skill.root", previousSkillRoot);
            restoreSystemProperty("user.dir", previousUserDir);
        }
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
