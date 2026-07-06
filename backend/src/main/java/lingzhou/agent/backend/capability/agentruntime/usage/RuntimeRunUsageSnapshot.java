package lingzhou.agent.backend.capability.agentruntime.usage;

import java.util.List;

public record RuntimeRunUsageSnapshot(
        String runStatus,
        boolean usageAvailable,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        int llmCallCount,
        int toolCallCount,
        long durationMs,
        long startedAtMillis,
        long completedAtMillis,
        Long modelId,
        String modelProvider,
        String modelName,
        String adapterType,
        List<RuntimeModelCallUsage> modelCalls) {}
