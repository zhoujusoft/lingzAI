package lingzhou.agent.backend.business.chat.controller;

import java.util.List;

public final class ConversationTokenUsageApiModels {

    private ConversationTokenUsageApiModels() {}

    public record DashboardResponse(
            RangeView range,
            SummaryView summary,
            List<TrendPointView> dailyTrend,
            List<BreakdownItemView> agentBreakdown,
            List<BreakdownItemView> userBreakdown,
            List<BreakdownItemView> modelBreakdown,
            List<RunItemView> spotlightRuns) {}

    public record RunListResponse(List<RunItemView> items, Integer pageNo, Integer pageSize, Long total) {}

    public record RangeView(String startDate, String endDate) {}

    public record SummaryView(
            Long runCount,
            Long usageRunCount,
            Long totalTokens,
            Long promptTokens,
            Long completionTokens,
            Long llmCallCount,
            Long toolCallCount,
            Long avgDurationMs,
            Long avgTokensPerRun,
            Long activeUserCount,
            Long activeAgentCount,
            Double usageCoverageRate) {}

    public record TrendPointView(
            String date, Long totalTokens, Long promptTokens, Long completionTokens, Long runCount) {}

    public record BreakdownItemView(
            String key,
            String label,
            String subtitle,
            Long runCount,
            Long totalTokens,
            Long promptTokens,
            Long completionTokens,
            Long llmCallCount,
            Long toolCallCount,
            Long avgTokensPerRun,
            Double shareRatio) {}

    public record RunItemView(
            Long assistantMessageId,
            Long userMessageId,
            Long sessionId,
            String sessionCode,
            String sessionType,
            String scopeType,
            Long scopeId,
            Long userId,
            String userName,
            String userCode,
            String agentType,
            Long agentId,
            String agentName,
            String runtimeSkillName,
            Long modelId,
            String modelProvider,
            String modelName,
            String adapterType,
            String runStatus,
            Boolean usageAvailable,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Integer llmCallCount,
            Integer toolCallCount,
            Long durationMs,
            String startedAt,
            String completedAt) {}
}
