package lingzhou.agent.backend.capability.agentruntime;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentRuntimeCapabilityRegistry {

    private final Map<RuntimeCapabilitySlot, AgentRuntimeCapability> capabilities;

    public AgentRuntimeCapabilityRegistry(List<AgentRuntimeCapability> runtimeCapabilities) {
        Map<RuntimeCapabilitySlot, AgentRuntimeCapability> values = new LinkedHashMap<>();
        List<AgentRuntimeCapability> orderedCapabilities = runtimeCapabilities == null
                ? List.of()
                : runtimeCapabilities.stream()
                        .sorted(Comparator.comparingInt(
                                capability -> capability.slot().order()))
                        .toList();
        for (AgentRuntimeCapability capability : orderedCapabilities) {
            AgentRuntimeCapability previous = values.putIfAbsent(capability.slot(), capability);
            if (previous != null) {
                throw new IllegalStateException("Duplicate agent runtime capability slot: " + capability.slot());
            }
        }
        this.capabilities = Map.copyOf(values);
    }

    public AgentRuntimeCapability require(RuntimeCapabilitySlot slot) {
        AgentRuntimeCapability capability = capabilities.get(slot);
        if (capability == null) {
            throw new IllegalStateException("Missing agent runtime capability: " + slot);
        }
        return capability;
    }
}
