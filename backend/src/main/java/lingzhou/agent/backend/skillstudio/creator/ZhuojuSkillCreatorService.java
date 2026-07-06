package lingzhou.agent.backend.skillstudio.creator;

import lingzhou.agent.backend.skillstudio.protocol.SkillStudioChangeProposal;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;

public interface ZhuojuSkillCreatorService {

    SkillStudioChangeProposal createProposal(SkillStudioContextInput input);
}
