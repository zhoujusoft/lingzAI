package lingzhou.agent.backend.business.chat.execution.model;

import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.provider.RuntimeProviderType;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;

public record RuntimeExecutionRequest(
        String sessionId,
        Long userId,
        Long runId,
        String runtimeSkillName,
        LingzRuntimeScopeType scopeType,
        Long scopeId,
        RuntimeProviderType provider,
        RuntimeExecutionMode mode,
        RuntimeExecutionAction action,
        RuntimeWorkspace workspace,
        Map<String, Object> payload,
        Long requestMessageId,
        Long assistantMessageId) {}
