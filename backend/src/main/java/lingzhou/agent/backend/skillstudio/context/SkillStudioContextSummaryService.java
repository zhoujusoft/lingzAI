package lingzhou.agent.backend.skillstudio.context;

import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;

public interface SkillStudioContextSummaryService {

    SkillStudioContextInput.DraftSummary buildDraftSummary(String skillName);

    SkillStudioContextInput.MemorySummary buildMemorySummary(String skillName);
}
