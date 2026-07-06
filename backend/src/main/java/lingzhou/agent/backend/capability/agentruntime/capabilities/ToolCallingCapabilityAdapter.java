package lingzhou.agent.backend.capability.agentruntime.capabilities;

import java.util.List;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeExecutionContext;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilityStatus;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareToolCallbackResolver;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareToolCallingManager;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.stereotype.Component;

@Component
public class ToolCallingCapabilityAdapter extends AbstractAgentRuntimeCapability {

    private final ModelRuntimeClientFactory modelRuntimeClientFactory;
    private final SkillKit skillKit;

    public ToolCallingCapabilityAdapter(ModelRuntimeClientFactory modelRuntimeClientFactory, SkillKit skillKit) {
        super(RuntimeCapabilitySlot.TOOL_CALLING, "tool-calling", RuntimeCapabilityStatus.ACTIVE);
        this.modelRuntimeClientFactory = modelRuntimeClientFactory;
        this.skillKit = skillKit;
    }

    public ModelRuntimeClientFactory.ChatRuntimeBundle createSkillChatBundle(
            AgentRuntimeExecutionContext executionContext) {
        ChatRuntimePreparedRequest prepared = executionContext == null ? null : executionContext.prepared();
        SkillKit effectiveSkillKit = executionContext != null && executionContext.requestSkillKit() != null
                ? executionContext.requestSkillKit()
                : skillKit;
        ToolCallingManager delegate = DefaultToolCallingManager.builder()
                .toolCallbackResolver(new DelegatingToolCallbackResolver(List.of(
                        new StaticToolCallbackResolver(prepared.toolCallbacks()),
                        SkillAwareToolCallbackResolver.builder()
                                .skillKit(effectiveSkillKit)
                                .build())))
                .build();
        ToolCallingManager toolCallingManager = SkillAwareToolCallingManager.builder()
                .skillKit(effectiveSkillKit)
                .delegate(delegate)
                .build();
        return modelRuntimeClientFactory.createChatBundleWithoutDefaultSystem(
                prepared == null ? null : prepared.chatModelId(), toolCallingManager);
    }
}
