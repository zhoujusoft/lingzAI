package lingzhou.agent.backend.capability.agentruntime.capabilities;

import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilityStatus;
import org.springframework.stereotype.Component;

@Component
public class SafetyGuardCapabilityAdapter extends AbstractAgentRuntimeCapability {

    public SafetyGuardCapabilityAdapter() {
        super(RuntimeCapabilitySlot.SAFETY_GUARD, "safety-guard", RuntimeCapabilityStatus.NOOP);
    }
}
