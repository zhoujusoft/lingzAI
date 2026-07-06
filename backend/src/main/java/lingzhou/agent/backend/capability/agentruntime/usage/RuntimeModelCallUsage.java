package lingzhou.agent.backend.capability.agentruntime.usage;

public record RuntimeModelCallUsage(
        int callNo,
        String status,
        boolean usageAvailable,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        int outputChars,
        long startedAtMillis,
        long completedAtMillis,
        long durationMs) {}
