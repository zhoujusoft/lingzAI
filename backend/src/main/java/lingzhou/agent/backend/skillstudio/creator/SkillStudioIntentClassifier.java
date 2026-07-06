package lingzhou.agent.backend.skillstudio.creator;

import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioIntent;

public interface SkillStudioIntentClassifier {

    SkillStudioIntent classify(SkillStudioContextInput input);
}
