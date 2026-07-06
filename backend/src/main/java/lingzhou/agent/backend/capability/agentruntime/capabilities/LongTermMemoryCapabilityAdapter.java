package lingzhou.agent.backend.capability.agentruntime.capabilities;

import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilityStatus;
import org.springframework.stereotype.Component;

@Component
public class LongTermMemoryCapabilityAdapter extends AbstractAgentRuntimeCapability {

    public LongTermMemoryCapabilityAdapter() {
        super(RuntimeCapabilitySlot.LONG_TERM_MEMORY, "long-term-memory", RuntimeCapabilityStatus.NOOP);
    }
}
