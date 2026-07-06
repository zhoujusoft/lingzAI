package lingzhou.agent.backend.skillstudio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillStudioCreatorToolProviderTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void shouldAllowWritingSkillMarkdownWithoutArgsAndWorkDir() {
        SkillStudioCreatorToolProvider provider = new SkillStudioCreatorToolProvider(workspaceRoot, "demo-skill");

        String writeSkillResult = provider.writeFile(
                "SKILL.md",
                """
                ---
                name: demo-skill
                description: "demo"
                ---

                # Demo

                run_python(
                    scriptPath="/skill/scripts/demo.py",
                    timeoutSeconds=30
                )
                """);
        String writeRequirementsResult = provider.writeFile("requirements.txt", "requests==2.32.3\n");
        String writeScriptResult = provider.writeFile("scripts/demo.py", "print('ok')\n");

        SkillStudioValidationResult result = provider.validateWrittenBundle();

        assertThat(writeSkillResult).contains("\"success\":true");
        assertThat(writeRequirementsResult).contains("\"success\":true");
        assertThat(writeScriptResult).contains("\"success\":true");
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldRejectReadingOtherSkillsWhileAllowingCurrentDraft() throws IOException {
        Path currentSkillFile = workspaceRoot.resolve("workspaces/public/skillstudio/draft/demo-skill/SKILL.md");
        Path otherSkillFile = workspaceRoot.resolve("workspaces/public/skillstudio/skills/copied-skill/SKILL.md");
        Files.createDirectories(currentSkillFile.getParent());
        Files.createDirectories(otherSkillFile.getParent());
        Files.writeString(currentSkillFile, "current");
        Files.writeString(otherSkillFile, "other");

        SkillStudioCreatorToolProvider provider = new SkillStudioCreatorToolProvider(workspaceRoot, "demo-skill");

        String currentResult = provider.readFile("workspaces/public/skillstudio/draft/demo-skill/SKILL.md");
        String otherResult = provider.readFile("workspaces/public/skillstudio/skills/copied-skill/SKILL.md");

        assertThat(currentResult).isEqualTo("current");
        assertThat(otherResult).contains("禁止读取其他 skill");
    }
}
