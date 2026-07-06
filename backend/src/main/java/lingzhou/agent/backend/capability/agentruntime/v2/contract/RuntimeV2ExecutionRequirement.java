package lingzhou.agent.backend.capability.agentruntime.v2.contract;

import java.util.List;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionBlockerSource;

public record RuntimeV2ExecutionRequirement(
        String code,
        String title,
        RuntimeV2CompletionBlockerSource source,
        String detail,
        String expectedAction,
        List<String> requiredEvidenceCodes,
        RuntimeV2EvidenceMatchMode evidenceMatchMode,
        List<String> preferredToolSequence,
        List<RuntimeV2TaskIntent> activationIntents) {

    public RuntimeV2ExecutionRequirement {
        requiredEvidenceCodes = requiredEvidenceCodes == null ? List.of() : List.copyOf(requiredEvidenceCodes);
        preferredToolSequence = preferredToolSequence == null ? List.of() : List.copyOf(preferredToolSequence);
        activationIntents = activationIntents == null ? List.of() : List.copyOf(activationIntents);
    }

    public boolean isActivatedBy(List<RuntimeV2TaskIntent> intents) {
        if (activationIntents.isEmpty()) {
            return true;
        }
        if (intents == null || intents.isEmpty()) {
            return false;
        }
        for (RuntimeV2TaskIntent intent : intents) {
            if (activationIntents.contains(intent)) {
                return true;
            }
        }
        return false;
    }
}
