package lingzhou.agent.backend.capability.agentruntime.v2.ledger;

import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionBlockerSource;

public record RuntimeV2ObligationEntry(
        String code,
        String title,
        RuntimeV2CompletionBlockerSource source,
        RuntimeV2ObligationStatus status,
        String detail,
        String expectedAction) {}
