package lingzhou.agent.backend.skillstudio.protocol;

import java.util.List;

public record SkillStudioChangeProposal(
        String skillName,
        SkillStudioMode mode,
        SkillStudioIntent intent,
        String summary,
        List<SkillStudioFileChange> changes,
        SkillStudioValidationResult validation,
        List<String> notes) {}
