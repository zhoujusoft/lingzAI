package lingzhou.agent.backend.skillstudio.context;

import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioContextInput;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioMode;

public interface SkillStudioContextAssembler {

    SkillStudioContextInput assemble(
            Long userId,
            Long projectId,
            String skillName,
            SkillStudioMode mode,
            String userGoal,
            String preferredTemplate,
            boolean preferMinimalChange,
            boolean allowCreateReferences)
            throws TaskException;
}
