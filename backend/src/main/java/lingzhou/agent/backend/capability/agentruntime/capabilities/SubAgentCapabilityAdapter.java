package lingzhou.agent.backend.capability.agentruntime.capabilities;

import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilityStatus;
import org.springframework.stereotype.Component;

@Component
public class SubAgentCapabilityAdapter extends AbstractAgentRuntimeCapability {

    public SubAgentCapabilityAdapter() {
        super(RuntimeCapabilitySlot.SUB_AGENT, "sub-agent", RuntimeCapabilityStatus.NOOP);
    }
}
