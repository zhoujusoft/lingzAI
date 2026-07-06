package lingzhou.agent.backend.capability.agentruntime.v2.completion;

public record RuntimeV2CompletionBlocker(
        String code, String title, RuntimeV2CompletionBlockerSource source, String detail, String expectedAction) {}
