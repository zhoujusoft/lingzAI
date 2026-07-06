package lingzhou.agent.backend.skillstudio.template;

import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioIntent;

public interface SkillStudioTemplateResolver {

    String resolveBaseTemplate(SkillStudioContextInput input, SkillStudioIntent intent, SkillStudioIntentMap intentMap);

    String loadTemplateContent(String templateName);
}
