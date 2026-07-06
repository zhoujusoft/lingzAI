package lingzhou.agent.backend.business.chat.service;

public record ConversationMessageUsagePayload(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Boolean usageAvailable,
        Integer llmCallCount,
        Integer toolCallCount,
        Long modelId,
        String modelProvider,
        String modelName,
        String adapterType,
        String usageSummaryJson) {}
