package lingzhou.agent.backend.skillstudio.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioChangeProposal;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioChangeType;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioFileChange;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioFileType;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioIntent;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioMode;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationResult;
import org.junit.jupiter.api.Test;

class DefaultSkillStudioValidationServiceTest {

    private final DefaultSkillStudioValidationService validationService = new DefaultSkillStudioValidationService();

    @Test
    void shouldAllowMinimalRunPythonDescriptionWhenSkillUsesPythonScripts() {
        String skillName = "demo-skill";
        SkillStudioChangeProposal proposal = new SkillStudioChangeProposal(
                skillName,
                SkillStudioMode.CREATE,
                new SkillStudioIntent(null, null, 1.0, List.of()),
                "create skill",
                List.of(
                        new SkillStudioFileChange(
                                "workspaces/public/skillstudio/draft/demo-skill/SKILL.md",
                                SkillStudioChangeType.CREATE,
                                SkillStudioFileType.SKILL,
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
                                """),
                        new SkillStudioFileChange(
                                "workspaces/public/skillstudio/draft/demo-skill/scripts/demo.py",
                                SkillStudioChangeType.CREATE,
                                SkillStudioFileType.SCRIPT,
                                "print('ok')\n"),
                        new SkillStudioFileChange(
                                "workspaces/public/skillstudio/draft/demo-skill/requirements.txt",
                                SkillStudioChangeType.CREATE,
                                SkillStudioFileType.REQUIREMENTS,
                                "requests==2.32.3\n")),
                null,
                List.of());

        SkillStudioValidationResult result = validationService.validateProposal(proposal);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
}
