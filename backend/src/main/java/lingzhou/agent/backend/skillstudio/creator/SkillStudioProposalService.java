package lingzhou.agent.backend.skillstudio.creator;

import lingzhou.agent.backend.skillstudio.protocol.SkillStudioChangeProposal;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioIntent;

public interface SkillStudioProposalService {

    SkillStudioChangeProposal propose(SkillStudioContextInput input, SkillStudioIntent intent, String templateName);
}
