package lingzhou.agent.backend.business.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lingzhou.agent.backend.business.chat.controller.ConversationTokenUsageApiModels;
import lingzhou.agent.backend.business.chat.domain.ConversationRunUsage;
import lingzhou.agent.backend.business.chat.mapper.ConversationRunUsageMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.enums.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConversationTokenUsageService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationTokenUsageService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    private static final int DEFAULT_LOOKBACK_DAYS = 14;
    private static final int DEFAULT_TOP_N = 6;
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final ConversationRunUsageMapper conversationRunUsageMapper;
    private final SysUserMapper sysUserMapper;

    public ConversationTokenUsageService(
            ConversationRunUsageMapper conversationRunUsageMapper, SysUserMapper sysUserMapper) {
        this.conversationRunUsageMapper = conversationRunUsageMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public ConversationTokenUsageApiModels.DashboardResponse getDashboard(
            Long operatorUserId,
            String startDate,
            String endDate,
            String agentType,
            Long agentId,
            Long userId,
            String sessionType,
            String modelProvider,
            String modelName,
            Integer topN) {
        QueryRange range = normalizeRange(startDate, endDate);
        Long effectiveUserId = resolveEffectiveUserId(operatorUserId, userId);
        int safeTopN = sanitizeTopN(topN);
        List<ConversationRunUsage> runs = conversationRunUsageMapper.selectList(buildQueryWrapper(
                range, agentType, agentId, effectiveUserId, sessionType, modelProvider, modelName, true));
        if (runs == null) {
            runs = List.of();
        }

        Map<Long, UserIdentity> userIdentityMap = loadUserIdentities(extractUserIds(runs));
        SummaryAccumulator summary = new SummaryAccumulator();
        Map<String, TrendAccumulator> dailyTrend = new TreeMap<>();
        Map<String, BucketAccumulator> agentBuckets = new LinkedHashMap<>();
        Map<String, BucketAccumulator> userBuckets = new LinkedHashMap<>();
        Map<String, BucketAccumulator> modelBuckets = new LinkedHashMap<>();

        for (ConversationRunUsage run : runs) {
            summary.add(run);

            String dayKey = formatLocalDate(run.getStartedAt());
            TrendAccumulator trend = dailyTrend.computeIfAbsent(dayKey, ignored -> new TrendAccumulator(dayKey));
            trend.add(run);

            BucketAccumulator agentBucket = agentBuckets.computeIfAbsent(
                    buildAgentKey(run),
                    ignored -> new BucketAccumulator(
                            buildAgentKey(run),
                            firstNonBlank(run.getAgentName(), buildAgentFallbackLabel(run)),
                            buildAgentSubtitle(run)));
            agentBucket.add(run);

            UserIdentity userIdentity = userIdentityMap.get(run.getUserId());
            BucketAccumulator userBucket = userBuckets.computeIfAbsent(
                    buildUserKey(run.getUserId()),
                    ignored -> new BucketAccumulator(
                            buildUserKey(run.getUserId()),
                            resolveUserLabel(run.getUserId(), userIdentity),
                            resolveUserSubtitle(run.getUserId(), userIdentity)));
            userBucket.add(run);

            BucketAccumulator modelBucket = modelBuckets.computeIfAbsent(
                    buildModelKey(run),
                    ignored -> new BucketAccumulator(
                            buildModelKey(run),
                            firstNonBlank(run.getModelName(), "未命名模型"),
                            firstNonBlank(run.getModelProvider(), "未识别提供方")));
            modelBucket.add(run);
        }

        long totalTokens = summary.totalTokens;
        return new ConversationTokenUsageApiModels.DashboardResponse(
                new ConversationTokenUsageApiModels.RangeView(
                        range.startDate().format(DATE_FORMATTER),
                        range.endDate().format(DATE_FORMATTER)),
                summary.toView(),
                dailyTrend.values().stream().map(TrendAccumulator::toView).toList(),
                sortBuckets(agentBuckets.values(), totalTokens, safeTopN),
                sortBuckets(userBuckets.values(), totalTokens, safeTopN),
                sortBuckets(modelBuckets.values(), totalTokens, 5),
                runs.stream()
                        .sorted(Comparator.comparingInt(this::safeRowTotalTokens)
                                .reversed()
                                .thenComparing(
                                        ConversationRunUsage::getStartedAt,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(
                                        ConversationRunUsage::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(4)
                        .map(run -> toRunItem(run, userIdentityMap.get(run.getUserId())))
                        .toList());
    }

    public ConversationTokenUsageApiModels.RunListResponse listRuns(
            Long operatorUserId,
            String startDate,
            String endDate,
            String agentType,
            Long agentId,
            Long userId,
            String sessionType,
            String modelProvider,
            String modelName,
            Integer pageNo,
            Integer pageSize) {
        QueryRange range = normalizeRange(startDate, endDate);
        Long effectiveUserId = resolveEffectiveUserId(operatorUserId, userId);
        int safePageNo = sanitizePageNo(pageNo);
        int safePageSize = sanitizePageSize(pageSize);

        Page<ConversationRunUsage> page = new Page<>(safePageNo, safePageSize);
        QueryWrapper<ConversationRunUsage> wrapper = buildQueryWrapper(
                range, agentType, agentId, effectiveUserId, sessionType, modelProvider, modelName, false);
        page = conversationRunUsageMapper.selectPage(page, wrapper);

        List<ConversationRunUsage> records = page.getRecords() == null ? List.of() : page.getRecords();
        Map<Long, UserIdentity> userIdentityMap = loadUserIdentities(extractUserIds(records));
        List<ConversationTokenUsageApiModels.RunItemView> items = records.stream()
                .map(run -> toRunItem(run, userIdentityMap.get(run.getUserId())))
                .toList();
        return new ConversationTokenUsageApiModels.RunListResponse(items, safePageNo, safePageSize, page.getTotal());
    }

    private QueryWrapper<ConversationRunUsage> buildQueryWrapper(
            QueryRange range,
            String agentType,
            Long agentId,
            Long userId,
            String sessionType,
            String modelProvider,
            String modelName,
            boolean orderAscending) {
        QueryWrapper<ConversationRunUsage> wrapper = new QueryWrapper<>();
        wrapper.ge(
                "started_at",
                toDate(range.startDate().atStartOfDay(DEFAULT_ZONE).toInstant()));
        wrapper.lt(
                "started_at",
                toDate(range.endDate().plusDays(1).atStartOfDay(DEFAULT_ZONE).toInstant()));
        if (StringUtils.hasText(agentType)) {
            wrapper.eq("agent_type", agentType.trim().toUpperCase(Locale.ROOT));
        }
        if (agentId != null) {
            wrapper.eq("agent_id", agentId);
        }
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (StringUtils.hasText(sessionType)) {
            wrapper.eq("session_type", sessionType.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(modelProvider)) {
            wrapper.eq("model_provider", modelProvider.trim());
        }
        if (StringUtils.hasText(modelName)) {
            wrapper.eq("model_name", modelName.trim());
        }
        if (orderAscending) {
            wrapper.orderByAsc("started_at").orderByAsc("id");
        } else {
            wrapper.orderByDesc("started_at").orderByDesc("id");
        }
        return wrapper;
    }

    private Map<Long, UserIdentity> loadUserIdentities(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserModel> users = sysUserMapper.selectBatchIds(userIds);
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        return users.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        SysUserModel::getId,
                        item -> new UserIdentity(item.getName(), item.getCode()),
                        (left, right) -> left));
    }

    private Long resolveEffectiveUserId(Long operatorUserId, Long requestedUserId) {
        if (operatorUserId == null) {
            return requestedUserId;
        }
        SysUserModel operator = sysUserMapper.selectById(operatorUserId);
        if (isAdminOperator(operator)) {
            return requestedUserId;
        }
        if (requestedUserId != null && !requestedUserId.equals(operatorUserId)) {
            logger.warn(
                    "token-usage 普通用户越权筛选已拦截：operatorUserId={}, requestedUserId={}", operatorUserId, requestedUserId);
        }
        return operatorUserId;
    }

    private boolean isAdminOperator(SysUserModel operator) {
        if (operator == null) {
            return false;
        }
        if (operator.getUserType() != null && operator.getUserType() == UserType.admin.getValue()) {
            return true;
        }
        return StringUtils.hasText(operator.getCode())
                && "admin".equalsIgnoreCase(operator.getCode().trim());
    }

    private Set<Long> extractUserIds(Collection<ConversationRunUsage> runs) {
        return runs.stream()
                .map(ConversationRunUsage::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private QueryRange normalizeRange(String startDate, String endDate) {
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        LocalDate resolvedEnd = parseDateOrNull(endDate);
        LocalDate resolvedStart = parseDateOrNull(startDate);
        if (resolvedEnd == null) {
            resolvedEnd = today;
        }
        if (resolvedStart == null) {
            resolvedStart = resolvedEnd.minusDays(DEFAULT_LOOKBACK_DAYS - 1L);
        }
        if (resolvedStart.isAfter(resolvedEnd)) {
            LocalDate temp = resolvedStart;
            resolvedStart = resolvedEnd;
            resolvedEnd = temp;
        }
        return new QueryRange(resolvedStart, resolvedEnd);
    }

    private LocalDate parseDateOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Date toDate(Instant instant) {
        return Date.from(instant);
    }

    private int sanitizeTopN(Integer topN) {
        if (topN == null || topN <= 0) {
            return DEFAULT_TOP_N;
        }
        return Math.min(topN, 20);
    }

    private int sanitizePageNo(Integer pageNo) {
        return pageNo == null || pageNo <= 0 ? DEFAULT_PAGE_NO : pageNo;
    }

    private int sanitizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private List<ConversationTokenUsageApiModels.BreakdownItemView> sortBuckets(
            Collection<BucketAccumulator> buckets, long totalTokens, int limit) {
        return buckets.stream()
                .sorted((left, right) -> {
                    int tokenCompare = Long.compare(right.totalTokens(), left.totalTokens());
                    if (tokenCompare != 0) {
                        return tokenCompare;
                    }
                    int runCompare = Long.compare(right.runCount(), left.runCount());
                    if (runCompare != 0) {
                        return runCompare;
                    }
                    return left.label().compareTo(right.label());
                })
                .limit(limit)
                .map(bucket -> bucket.toView(totalTokens))
                .toList();
    }

    private ConversationTokenUsageApiModels.RunItemView toRunItem(ConversationRunUsage run, UserIdentity userIdentity) {
        return new ConversationTokenUsageApiModels.RunItemView(
                run.getAssistantMessageId(),
                run.getUserMessageId(),
                run.getSessionId(),
                run.getSessionCode(),
                run.getSessionType(),
                run.getScopeType(),
                run.getScopeId(),
                run.getUserId(),
                resolveUserLabel(run.getUserId(), userIdentity),
                userIdentity == null ? null : userIdentity.userCode(),
                run.getAgentType(),
                run.getAgentId(),
                firstNonBlank(run.getAgentName(), buildAgentFallbackLabel(run)),
                run.getRuntimeSkillName(),
                run.getModelId(),
                run.getModelProvider(),
                run.getModelName(),
                run.getAdapterType(),
                run.getRunStatus(),
                Boolean.TRUE.equals(run.getUsageAvailable()),
                run.getPromptTokens(),
                run.getCompletionTokens(),
                run.getTotalTokens(),
                run.getLlmCallCount(),
                run.getToolCallCount(),
                run.getDurationMs(),
                formatDateTime(run.getStartedAt()),
                formatDateTime(run.getCompletedAt()));
    }

    private String formatLocalDate(Date value) {
        if (value == null) {
            return "";
        }
        return value.toInstant().atZone(DEFAULT_ZONE).toLocalDate().format(DATE_FORMATTER);
    }

    private String formatDateTime(Date value) {
        if (value == null) {
            return null;
        }
        return value.toInstant().atZone(DEFAULT_ZONE).toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String buildAgentKey(ConversationRunUsage run) {
        String type = firstNonBlank(run.getAgentType(), "UNKNOWN");
        String idText = run.getAgentId() == null ? "NA" : String.valueOf(run.getAgentId());
        return type + ":" + idText;
    }

    private String buildAgentFallbackLabel(ConversationRunUsage run) {
        String type = firstNonBlank(run.getAgentType(), "UNKNOWN");
        if (run.getAgentId() == null) {
            return type;
        }
        return type + " #" + run.getAgentId();
    }

    private String buildAgentSubtitle(ConversationRunUsage run) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(run.getSessionType())) {
            parts.add(run.getSessionType().trim());
        }
        if (StringUtils.hasText(run.getRuntimeSkillName())) {
            parts.add(run.getRuntimeSkillName().trim());
        }
        return parts.isEmpty() ? "运行实体" : String.join(" · ", parts);
    }

    private String buildUserKey(Long userId) {
        return userId == null ? "user:unknown" : "user:" + userId;
    }

    private String resolveUserLabel(Long userId, UserIdentity userIdentity) {
        if (userIdentity != null && StringUtils.hasText(userIdentity.userName())) {
            return userIdentity.userName().trim();
        }
        if (userIdentity != null && StringUtils.hasText(userIdentity.userCode())) {
            return userIdentity.userCode().trim();
        }
        return userId == null ? "未知用户" : "用户 #" + userId;
    }

    private String resolveUserSubtitle(Long userId, UserIdentity userIdentity) {
        if (userIdentity != null && StringUtils.hasText(userIdentity.userCode())) {
            return "账号 " + userIdentity.userCode().trim();
        }
        return userId == null ? "无绑定账号" : "ID " + userId;
    }

    private String buildModelKey(ConversationRunUsage run) {
        return firstNonBlank(run.getModelProvider(), "UNKNOWN_PROVIDER") + ":"
                + firstNonBlank(run.getModelName(), "UNKNOWN_MODEL");
    }

    private int safeRowTotalTokens(ConversationRunUsage run) {
        return safeInt(run == null ? null : run.getTotalTokens());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String firstNonBlank(String first, String fallback) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return fallback;
    }

    private final class SummaryAccumulator {

        private long runCount;
        private long usageRunCount;
        private long totalTokens;
        private long promptTokens;
        private long completionTokens;
        private long llmCallCount;
        private long toolCallCount;
        private long totalDurationMs;
        private final Set<Long> activeUserIds = new java.util.HashSet<>();
        private final Set<String> activeAgentKeys = new java.util.HashSet<>();

        private void add(ConversationRunUsage run) {
            runCount += 1;
            if (Boolean.TRUE.equals(run.getUsageAvailable())) {
                usageRunCount += 1;
            }
            totalTokens += safeInt(run.getTotalTokens());
            promptTokens += safeInt(run.getPromptTokens());
            completionTokens += safeInt(run.getCompletionTokens());
            llmCallCount += safeInt(run.getLlmCallCount());
            toolCallCount += safeInt(run.getToolCallCount());
            totalDurationMs += safeLong(run.getDurationMs());
            if (run.getUserId() != null) {
                activeUserIds.add(run.getUserId());
            }
            activeAgentKeys.add(buildAgentKey(run));
        }

        private ConversationTokenUsageApiModels.SummaryView toView() {
            long avgDuration = runCount <= 0 ? 0L : totalDurationMs / runCount;
            long avgTokens = usageRunCount <= 0 ? 0L : totalTokens / usageRunCount;
            double coverage = runCount <= 0 ? 0D : (usageRunCount * 100D) / runCount;
            return new ConversationTokenUsageApiModels.SummaryView(
                    runCount,
                    usageRunCount,
                    totalTokens,
                    promptTokens,
                    completionTokens,
                    llmCallCount,
                    toolCallCount,
                    avgDuration,
                    avgTokens,
                    (long) activeUserIds.size(),
                    (long) activeAgentKeys.size(),
                    coverage);
        }
    }

    private final class TrendAccumulator {

        private final String date;
        private long totalTokens;
        private long promptTokens;
        private long completionTokens;
        private long runCount;

        private TrendAccumulator(String date) {
            this.date = date;
        }

        private void add(ConversationRunUsage run) {
            runCount += 1;
            totalTokens += safeInt(run.getTotalTokens());
            promptTokens += safeInt(run.getPromptTokens());
            completionTokens += safeInt(run.getCompletionTokens());
        }

        private ConversationTokenUsageApiModels.TrendPointView toView() {
            return new ConversationTokenUsageApiModels.TrendPointView(
                    date, totalTokens, promptTokens, completionTokens, runCount);
        }
    }

    private final class BucketAccumulator {

        private final String key;
        private final String label;
        private final String subtitle;
        private long runCount;
        private long totalTokens;
        private long promptTokens;
        private long completionTokens;
        private long llmCallCount;
        private long toolCallCount;

        private BucketAccumulator(String key, String label, String subtitle) {
            this.key = key;
            this.label = label;
            this.subtitle = subtitle;
        }

        private void add(ConversationRunUsage run) {
            runCount += 1;
            totalTokens += safeInt(run.getTotalTokens());
            promptTokens += safeInt(run.getPromptTokens());
            completionTokens += safeInt(run.getCompletionTokens());
            llmCallCount += safeInt(run.getLlmCallCount());
            toolCallCount += safeInt(run.getToolCallCount());
        }

        private long totalTokens() {
            return totalTokens;
        }

        private long runCount() {
            return runCount;
        }

        private String label() {
            return label;
        }

        private ConversationTokenUsageApiModels.BreakdownItemView toView(long overallTotalTokens) {
            long averageTokens = runCount <= 0 ? 0L : totalTokens / runCount;
            double ratio = overallTotalTokens <= 0 ? 0D : (totalTokens * 100D) / overallTotalTokens;
            return new ConversationTokenUsageApiModels.BreakdownItemView(
                    key,
                    label,
                    subtitle,
                    runCount,
                    totalTokens,
                    promptTokens,
                    completionTokens,
                    llmCallCount,
                    toolCallCount,
                    averageTokens,
                    ratio);
        }
    }

    private record QueryRange(LocalDate startDate, LocalDate endDate) {}

    private record UserIdentity(String userName, String userCode) {}
}
