package lingzhou.agent.backend.capability.agentruntime;

import org.springframework.stereotype.Component;

@Component
public class AgentRuntimeFactory {

    private final StaticAgentRuntimeProfileRegistry profileRegistry;
    private final AgentRuntimeCapabilityRegistry capabilityRegistry;

    public AgentRuntimeFactory(
            StaticAgentRuntimeProfileRegistry profileRegistry, AgentRuntimeCapabilityRegistry capabilityRegistry) {
        this.profileRegistry = profileRegistry;
        this.capabilityRegistry = capabilityRegistry;
    }

    public AgentRuntime create(AgentRuntimeProfile profile) {
        AgentRuntimeProfileDefinition definition = profileRegistry.require(profile);
        AgentRuntime.Builder builder = AgentRuntime.builder(definition.profile());
        for (RuntimeCapabilitySlot slot : definition.capabilitySlots()) {
            capabilityRegistry.require(slot).contribute(builder);
        }
        return builder.build();
    }
}
