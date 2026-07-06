package lingzhou.agent.backend.skillstudio.validate;

import lingzhou.agent.backend.skillstudio.protocol.SkillStudioChangeProposal;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationResult;

public interface SkillStudioValidationService {

    SkillStudioValidationResult validateProposal(SkillStudioChangeProposal proposal);

    SkillStudioValidationResult validateSkillContent(String skillName, String skillMdContent);
}
