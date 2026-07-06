package lingzhou.agent.backend.capability.agentruntime.v2.completion;

public record RuntimeV2CompletionEvidence(
        String code, String title, RuntimeV2CompletionEvidenceSource source, boolean satisfied, String detail) {}
