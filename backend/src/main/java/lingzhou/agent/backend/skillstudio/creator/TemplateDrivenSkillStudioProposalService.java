package lingzhou.agent.backend.skillstudio.creator;

import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioChangeProposal;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioChangeType;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioFileChange;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioFileType;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioIntent;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationResult;
import org.springframework.stereotype.Service;

@Service
public class TemplateDrivenSkillStudioProposalService implements SkillStudioProposalService {

    @Override
    public SkillStudioChangeProposal propose(
            SkillStudioContextInput input, SkillStudioIntent intent, String templateName) {
        String skillName = input.skillName();
        List<SkillStudioFileChange> changes = new ArrayList<>();
        changes.add(new SkillStudioFileChange(
                SkillStudioWorkspacePaths.DRAFT_ROOT + "/" + skillName + "/SKILL.md",
                input.mode().name().equals("CREATE") ? SkillStudioChangeType.CREATE : SkillStudioChangeType.UPDATE,
                SkillStudioFileType.SKILL,
                buildSkillMd(input, intent)));
        for (String referenceName : suggestReferences(input, intent, templateName)) {
            changes.add(new SkillStudioFileChange(
                    SkillStudioWorkspacePaths.DRAFT_ROOT + "/" + skillName + "/references/" + referenceName,
                    SkillStudioChangeType.CREATE,
                    SkillStudioFileType.REFERENCE,
                    buildReferenceContent(referenceName, input)));
        }
        return new SkillStudioChangeProposal(
                skillName,
                input.mode(),
                intent,
                "基于上下文生成 skill draft 提案",
                List.copyOf(changes),
                new SkillStudioValidationResult(true, List.of(), List.of()),
                List.of("结构倾向: " + templateName, "能力模板: " + intent.capabilityTemplate()));
    }

    private String buildSkillMd(SkillStudioContextInput input, SkillStudioIntent intent) {
        String title = toTitle(input.skillName());
        return """
                ---
                name: %s
                description: "%s"
                ---

                # %s

                用于处理以下场景：
                - %s

                ## 何时使用

                当用户想要：
                - %s

                使用本 skill。

                ## 工作流

                1. 先理解用户输入和目标产物。
                2. 需要稳定规则或格式契约时，按需读取 references。
                3. 如果涉及文件、脚本或产物输出，使用独立 runtime 工具描述运行时动作，例如 `file_read(...)`、`file_write(...)`、`run_python(...)`、`write_artifact(...)`。
                4. 完成后给出结果摘要和必要的下载入口。

                ## 结构倾向

                `%s` 只是历史兼容字段，不代表需要套用通用结构模板。
                """
                .formatted(
                        input.skillName(),
                        defaultDescription(input.userGoal()),
                        title,
                        defaultGoalLine(input.userGoal()),
                        defaultGoalLine(input.userGoal()),
                        intent.baseTemplate());
    }

    private List<String> suggestReferences(
            SkillStudioContextInput input, SkillStudioIntent intent, String templateName) {
        boolean allowCreateReferences = input.hints() != null && input.hints().allowCreateReferences();
        if (!allowCreateReferences) {
            return List.of();
        }
        if ("document-workflow".equals(templateName)) {
            return List.of("inputs.md", "workflow.md");
        }
        if ("scripted-workflow".equals(templateName)) {
            return List.of("workflow.md");
        }
        if ("basic".equals(templateName) || "minimal".equals(templateName)) {
            return List.of();
        }
        if ("knowledge-base".equals(intent.capabilityTemplate())) {
            return List.of("answer-boundary.md");
        }
        if ("api-library".equals(intent.capabilityTemplate())) {
            return List.of("inputs.md", "workflow.md");
        }
        if ("dataset".equals(intent.capabilityTemplate())) {
            return "document-workflow".equals(templateName) || "scripted-workflow".equals(templateName)
                    ? List.of("inputs.md", "workflow.md")
                    : List.of("dataset-guide.md");
        }
        if ("mcp-library".equals(intent.capabilityTemplate())) {
            return "basic".equals(templateName) || "minimal".equals(templateName)
                    ? List.of()
                    : List.of("lookup-rules.md");
        }
        return List.of();
    }

    private String buildReferenceContent(String referenceName, SkillStudioContextInput input) {
        String title = referenceName.replace(".md", "").replace("-", " ");
        return """
                # %s

                ## Purpose
                用于补充 `%s` 对应场景的稳定说明。

                ## Key Rules
                - 基于当前用户目标补充规则
                - 后续由真实场景继续细化
                """
                .formatted(toTitle(title), input.skillName());
    }

    private String defaultDescription(String userGoal) {
        String value = defaultGoalLine(userGoal);
        return value.length() > 48 ? value.substring(0, 48) : value;
    }

    private String defaultGoalLine(String userGoal) {
        return (userGoal == null || userGoal.isBlank()) ? "处理当前 skill 目标" : userGoal.trim();
    }

    private String toTitle(String value) {
        if (value == null || value.isBlank()) {
            return "Skill";
        }
        String[] parts = value.replace("_", "-").split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
