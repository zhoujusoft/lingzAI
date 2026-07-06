package lingzhou.agent.backend.business.chat.execution.workspace;

import lingzhou.agent.backend.business.chat.execution.model.RuntimeWorkspace;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;

public interface RuntimeWorkspaceResolver {

    RuntimeWorkspace resolve(
            Long userId, String sessionId, String runtimeSkillName, LingzRuntimeScopeType scopeType, Long scopeId);
}
