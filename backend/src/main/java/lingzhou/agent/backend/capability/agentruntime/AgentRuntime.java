package lingzhou.agent.backend.capability.agentruntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AgentRuntime {

    private final AgentRuntimeProfile profile;
    private final Map<RuntimeCapabilitySlot, AgentRuntimeCapability> capabilities;

    private AgentRuntime(AgentRuntimeProfile profile, Map<RuntimeCapabilitySlot, AgentRuntimeCapability> capabilities) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.capabilities = Collections.unmodifiableMap(new LinkedHashMap<>(capabilities));
    }

    public static Builder builder(AgentRuntimeProfile profile) {
        return new Builder(profile);
    }

    public AgentRuntimeProfile profile() {
        return profile;
    }

    public boolean hasCapability(RuntimeCapabilitySlot slot) {
        return capabilities.containsKey(slot);
    }

    public boolean hasActiveCapability(RuntimeCapabilitySlot slot) {
        AgentRuntimeCapability capability = capabilities.get(slot);
        return capability != null && capability.status() == RuntimeCapabilityStatus.ACTIVE;
    }

    public Optional<AgentRuntimeCapability> capability(RuntimeCapabilitySlot slot) {
        return Optional.ofNullable(capabilities.get(slot));
    }

    public List<AgentRuntimeCapability> capabilities() {
        return List.copyOf(capabilities.values());
    }

    public List<String> capabilityNames() {
        List<String> values = new ArrayList<>();
        for (AgentRuntimeCapability capability : capabilities.values()) {
            values.add(capability.name() + ":" + capability.status().name());
        }
        return values;
    }

    public static final class Builder {

        private final AgentRuntimeProfile profile;
        private final Map<RuntimeCapabilitySlot, AgentRuntimeCapability> capabilities = new LinkedHashMap<>();

        private Builder(AgentRuntimeProfile profile) {
            this.profile = Objects.requireNonNull(profile, "profile");
        }

        public Builder addCapability(AgentRuntimeCapability capability) {
            if (capability == null) {
                return this;
            }
            capabilities.put(capability.slot(), capability);
            return this;
        }

        public AgentRuntime build() {
            return new AgentRuntime(profile, capabilities);
        }
    }
}
