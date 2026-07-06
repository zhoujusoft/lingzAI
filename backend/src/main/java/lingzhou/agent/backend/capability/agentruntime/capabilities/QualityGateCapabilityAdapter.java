package lingzhou.agent.backend.capability.agentruntime.capabilities;

import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilityStatus;
import org.springframework.stereotype.Component;

@Component
public class QualityGateCapabilityAdapter extends AbstractAgentRuntimeCapability {

    public QualityGateCapabilityAdapter() {
        super(RuntimeCapabilitySlot.QUALITY_GATE, "quality-gate", RuntimeCapabilityStatus.NOOP);
    }
}
