package lingzhou.agent.backend.capability.agentruntime;

import java.util.Objects;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.spring.ai.skill.core.SkillKit;

public record AgentRuntimeExecutionContext(
        ChatRuntimePreparedRequest prepared,
        Long userId,
        ConversationHistoryService.ConversationContext conversation,
        AgentRuntimeProfileResolution profileResolution,
        AgentRuntime agentRuntime,
        SkillKit requestSkillKit,
        RequestScopedSkillRuntimeService requestScopedSkillRuntimeService) {

    public AgentRuntimeExecutionContext {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(profileResolution, "profileResolution");
        Objects.requireNonNull(agentRuntime, "agentRuntime");
        Objects.requireNonNull(requestScopedSkillRuntimeService, "requestScopedSkillRuntimeService");
    }

    public AgentRuntimeProfile profile() {
        return profileResolution.profile();
    }

    public AgentRuntimePipeline pipeline() {
        return profileResolution.pipeline();
    }

    public boolean usesToolAwarePipeline() {
        return profileResolution.usesToolAwarePipeline();
    }

    public boolean hasActiveCapability(RuntimeCapabilitySlot slot) {
        return agentRuntime.hasActiveCapability(slot);
    }

    public AgentRuntimeExecutionContext withPrepared(ChatRuntimePreparedRequest nextPrepared) {
        return new AgentRuntimeExecutionContext(
                nextPrepared,
                userId,
                conversation,
                profileResolution,
                agentRuntime,
                requestSkillKit,
                requestScopedSkillRuntimeService);
    }
}
