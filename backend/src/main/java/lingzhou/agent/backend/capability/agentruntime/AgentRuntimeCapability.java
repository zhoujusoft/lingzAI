package lingzhou.agent.backend.capability.agentruntime;

public interface AgentRuntimeCapability {

    RuntimeCapabilitySlot slot();

    String name();

    RuntimeCapabilityStatus status();

    default void contribute(AgentRuntime.Builder builder) {
        builder.addCapability(this);
    }
}
