package lingzhou.agent.backend.skillstudio.protocol;

import java.util.List;

public record SkillStudioValidationResult(
        boolean valid, List<SkillStudioValidationIssue> errors, List<SkillStudioValidationIssue> warnings) {}
