package lingzhou.agent.backend.capability.agentruntime;

import java.util.List;
import java.util.Objects;

public record AgentRuntimeProfileDefinition(AgentRuntimeProfile profile, List<RuntimeCapabilitySlot> capabilitySlots) {

    public AgentRuntimeProfileDefinition {
        profile = Objects.requireNonNull(profile, "profile");
        capabilitySlots = capabilitySlots == null ? List.of() : List.copyOf(capabilitySlots);
    }

    public boolean contains(RuntimeCapabilitySlot slot) {
        return capabilitySlots.contains(slot);
    }
}
