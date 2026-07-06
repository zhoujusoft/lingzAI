package lingzhou.agent.backend.capability.agentruntime.v2.ledger;

import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionEvidenceSource;

public record RuntimeV2EvidenceEntry(
        String code,
        String title,
        RuntimeV2CompletionEvidenceSource source,
        RuntimeV2EvidenceStatus status,
        String detail,
        String sourceRef) {}
