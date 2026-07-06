package lingzhou.agent.backend.capability.agentruntime.capabilities;

import java.util.Objects;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeCapability;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilityStatus;

abstract class AbstractAgentRuntimeCapability implements AgentRuntimeCapability {

    private final RuntimeCapabilitySlot slot;
    private final String name;
    private final RuntimeCapabilityStatus status;

    protected AbstractAgentRuntimeCapability(RuntimeCapabilitySlot slot, String name, RuntimeCapabilityStatus status) {
        this.slot = Objects.requireNonNull(slot, "slot");
        this.name = Objects.requireNonNull(name, "name");
        this.status = Objects.requireNonNull(status, "status");
    }

    @Override
    public RuntimeCapabilitySlot slot() {
        return slot;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public RuntimeCapabilityStatus status() {
        return status;
    }
}
