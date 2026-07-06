package lingzhou.agent.backend.business.chat.service;

import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lingzhou.agent.backend.app.ChatContextProperties;
import lingzhou.agent.backend.business.chat.domain.ConversationEvent;
import lingzhou.agent.backend.business.chat.domain.ConversationMessage;
import lingzhou.agent.backend.business.chat.domain.ConversationSession;
import lingzhou.agent.backend.business.chat.mapper.ConversationEventMapper;
import lingzhou.agent.backend.business.chat.mapper.ConversationMessageMapper;
import lingzhou.agent.backend.business.chat.mapper.ConversationSessionMapper;
import lingzhou.agent.backend.business.chat.util.UlidGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ConversationEventService {

    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern INLINE_ARTIFACT_URL_PATTERN =
            Pattern.compile("(?:artifact://\\S+|/api/files/artifacts/\\S+)");
    private static final Pattern ASSISTANT_HISTORY_SPLIT_PATTERN = Pattern.compile("\\n\\s*\\n");
    private static final int PROMPT_HISTORY_SYSTEM_EVENT_LIMIT = 4;
    private static final int PROMPT_HISTORY_SYSTEM_SUMMARY_CHAR_LIMIT = 360;
    private static final List<String> ASSISTANT_EXECUTION_PREFIXES = List.of(
            "我会",
            "我将",
            "让我",
            "首先",
            "接下来",
            "正在",
            "看起来",
            "我注意到",
            "根据技能要求",
            "按步骤",
            "需要先");
    private static final List<String> ASSISTANT_EXECUTION_HINTS = List.of(
            "加载",
            "查询",
            "执行",
            "生成",
            "尝试",
            "确认",
            "数据集",
            "工具",
            "sql",
            "脚本",
            "预览",
            "对象",
            "结构");

    private final ConversationEventMapper conversationEventMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final ConversationSessionMapper conversationSessionMapper;
    private final ChatContextProperties chatContextProperties;
    private final ConversationEventTypeConfigRegistry eventTypeConfigRegistry;

    public ConversationEventService(
            ConversationEventMapper conversationEventMapper,
            ConversationMessageMapper conversationMessageMapper,
            ConversationSessionMapper conversationSessionMapper,
            ChatContextProperties chatContextProperties,
            ConversationEventTypeConfigRegistry eventTypeConfigRegistry) {
        this.conversationEventMapper = conversationEventMapper;
        this.conversationMessageMapper = conversationMessageMapper;
        this.conversationSessionMapper = conversationSessionMapper;
        this.chatContextProperties = chatContextProperties;
        this.eventTypeConfigRegistry = eventTypeConfigRegistry;
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertAssistantMessage(
            ConversationHistoryService.ConversationContext context,
            String content,
            String messageType,
            String metadataJson) {
        upsertAssistantMessage(context, content, null, messageType, metadataJson, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertAssistantMessage(
            ConversationHistoryService.ConversationContext context,
            String content,
            String messageType,
            String metadataJson,
            String artifactSummaryJson) {
        upsertAssistantMessage(context, content, null, messageType, metadataJson, artifactSummaryJson);
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertAssistantMessage(
            ConversationHistoryService.ConversationContext context,
            String content,
            String segmentsJson,
            String messageType,
            String metadataJson,
            String artifactSummaryJson) {
        if (context == null || context.assistantMessageId() == null) {
            return;
        }
        String normalizedContent = normalizeText(content);
        ConversationMessage update = new ConversationMessage();
        update.setId(context.assistantMessageId());
        update.setContent(normalizedContent);
        update.setSegmentsJson(normalizeNullableText(segmentsJson));
        update.setParamsJson(normalizeNullableText(metadataJson));
        update.setArtifactSummaryJson(normalizeNullableText(artifactSummaryJson));
        conversationMessageMapper.updateById(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertToolTraceMessage(
            ConversationHistoryService.ConversationContext context,
            String toolCallId,
            String content,
            String contentJson,
            String metadataJson) {
        if (context == null || context.assistantMessageId() == null || !StringUtils.hasText(toolCallId)) {
            return;
        }
        String normalizedContent = normalizeText(content);
        if (!StringUtils.hasText(normalizedContent)) {
            return;
        }
        String normalizedToolCallId = normalizeNullableText(toolCallId);
        ConversationEvent existing = conversationEventMapper.selectLatestByMessageIdAndEventTypeAndSubtype(
                context.assistantMessageId(), "TOOL_FINISHED", normalizedToolCallId);
        if (existing != null && existing.getId() != null) {
            ConversationEvent update = new ConversationEvent();
            update.setId(existing.getId());
            update.setSummaryText(normalizeNullableText(normalizedContent));
            update.setPayloadJson(normalizeNullableText(contentJson));
            conversationEventMapper.updateById(update);
            return;
        }
        appendEvent(
                context,
                context.assistantMessageId(),
                "TOOL_FINISHED",
                normalizedToolCallId,
                normalizedContent,
                contentJson);
    }

    @Transactional(rollbackFor = Exception.class)
    public void appendEvent(
            ConversationHistoryService.ConversationContext context,
            Long messageId,
            String eventType,
            String eventSubtype,
            String summaryText,
            String payloadJson) {
        appendRunEvent(
                context, null, messageId, eventType, eventSubtype, null, null, null, null, summaryText, payloadJson);
    }

    @Transactional(rollbackFor = Exception.class)
    public void appendRunEvent(
            ConversationHistoryService.ConversationContext context,
            Long runId,
            Long messageId,
            String eventType,
            String eventSubtype,
            String phase,
            String subStage,
            String toolName,
            String eventStatus,
            String summaryText,
            String payloadJson) {
        if (context == null || context.sessionId() == null || !StringUtils.hasText(eventType)) {
            return;
        }
        ConversationEvent entity = new ConversationEvent();
        entity.setSessionId(context.sessionId());
        entity.setMessageId(messageId);
        entity.setRunId(runId);
        entity.setEventCode(UlidGenerator.next());
        entity.setEventSubtype(normalizeNullableText(eventSubtype));
        entity.setEventType(eventType.trim().toUpperCase());
        entity.setPhase(normalizeNullableText(phase));
        entity.setSubStage(normalizeNullableText(subStage));
        entity.setToolName(normalizeNullableText(toolName));
        entity.setEventStatus(normalizeNullableText(eventStatus));
        entity.setSequenceNo(conversationEventMapper.countBySessionId(context.sessionId()) + 1);
        entity.setSummaryText(normalizeNullableText(summaryText));
        entity.setPayloadJson(normalizeNullableText(payloadJson));
        entity.setCreateUserId(context.createUserId());
        conversationEventMapper.insert(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void appendSummaryMessage(
            Long sessionId,
            String sessionIdText,
            Long createUserId,
            String content,
            Integer compressedUntilMessageSequence,
            Integer compressedUntilEventSequence,
            Integer compressedMessageCount,
            Integer summaryVersion) {
        if (sessionId == null || sessionId <= 0 || !StringUtils.hasText(content)) {
            return;
        }
        ConversationEvent entity = new ConversationEvent();
        entity.setSessionId(sessionId);
        entity.setEventCode(UlidGenerator.next());
        entity.setEventType("SUMMARY_SNAPSHOT");
        entity.setSequenceNo(conversationEventMapper.countBySessionId(sessionId) + 1);
        entity.setSummaryText(content.trim());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "compressedUntilMessageSequence",
                compressedUntilMessageSequence == null ? 0 : compressedUntilMessageSequence);
        payload.put(
                "compressedUntilEventSequence",
                compressedUntilEventSequence == null ? 0 : compressedUntilEventSequence);
        payload.put("compressedMessageCount", compressedMessageCount == null ? 0 : compressedMessageCount);
        payload.put("summaryVersion", summaryVersion == null ? 1 : summaryVersion);
        payload.put("sessionId", normalizeText(sessionIdText));
        entity.setPayloadJson(JSON.toJSONString(payload));
        entity.setCreateUserId(createUserId);
        conversationEventMapper.insert(entity);
    }

    public List<Message> buildSpringHistoryMessages(String sessionId, Long excludedMessageId) {
        return buildSpringConversationHistoryMessages(sessionId, excludedMessageId);
    }

    public List<Message> buildSpringConversationHistoryMessages(String sessionId, Long excludedMessageId) {
        ConversationSession session = resolveSession(sessionId);
        if (session == null || session.getId() == null) {
            return List.of();
        }
        int maxMessages = Math.max(1, chatContextProperties.getMaxMessages());
        int fetchLimit = Math.max(maxMessages * 3, 120);
        ConversationEvent latestSummary = conversationEventMapper.selectLatestSummary(session.getId());
        int summaryMessageSequence = extractCompressedUntilSequence(latestSummary, "compressedUntilMessageSequence");

        List<MemoryItem> items = new ArrayList<>();
        List<ConversationMessage> messages = conversationMessageMapper.selectRecentMessagesAfterSequence(
                session.getId(), summaryMessageSequence, fetchLimit, excludedMessageId);
        for (ConversationMessage message : messages) {
            MemoryItem item = toMemoryItem(message);
            if (item != null) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            return List.of();
        }

        items.sort(Comparator.comparing(MemoryItem::createdAt).thenComparing(MemoryItem::orderKey));
        List<MemoryItem> window = buildHistoryWindow(items, maxMessages);
        log.debug(
                "对话历史窗口已装配：sessionId={}, candidates={}, selected={}, estimatedTokens={}, maxMessages={}, maxHistoryTokens={}",
                session.getId(),
                items.size(),
                window.size(),
                sumEstimatedTokens(window),
                maxMessages,
                Math.max(0, chatContextProperties.getMaxHistoryTokens()));
        return window.stream()
                .map(this::toSpringMessage)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public String buildExecutionStateSummary(String sessionId) {
        ConversationSession session = resolveSession(sessionId);
        if (session == null || session.getId() == null) {
            return "";
        }
        ConversationEvent latestSummary = conversationEventMapper.selectLatestSummary(session.getId());
        int summaryEventSequence = extractCompressedUntilSequence(latestSummary, "compressedUntilEventSequence");
        List<ConversationEvent> events = conversationEventMapper.selectRecentBySessionId(
                session.getId(), summaryEventSequence, 12, executionStateEventTypes());
        List<String> successfulActions = new ArrayList<>();
        List<String> failedOrBlockedActions = new ArrayList<>();
        List<String> pendingDecisions = new ArrayList<>();
        for (ConversationEvent event : events) {
            if (event == null || !StringUtils.hasText(event.getEventType())) {
                continue;
            }
            String eventType = event.getEventType().trim().toUpperCase();
            String text = eventTypeConfigRegistry.resolveMemoryText(eventType, event.getSummaryText(), event.getPayloadJson());
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String compact = shrinkPlainText(text, 120);
            switch (eventType) {
                case "TOOL_FINISHED", "ARTIFACT_READY", "EXECUTION_STEP_COMPLETED" -> addUnique(successfulActions, compact, 4);
                case "TOOL_FAILED", "EXECUTION_PRECHECK_BLOCKED", "EXECUTION_STEP_FAILED", "MESSAGE_FAILED" ->
                        addUnique(failedOrBlockedActions, compact, 4);
                case "EXECUTION_CONFIRMATION_REQUIRED" -> addUnique(pendingDecisions, compact, 3);
                default -> {
                    // Ignore other execution events in state summary.
                }
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("## 当前运行状态");
        lines.add("以下内容来自系统执行状态，不属于自然对话历史。");
        if (latestSummary != null && StringUtils.hasText(latestSummary.getSummaryText())) {
            lines.add("- 较早上下文摘要：" + shrinkPlainText(latestSummary.getSummaryText(), 160));
        }
        if (!successfulActions.isEmpty()) {
            lines.add("- Successful external actions:");
            successfulActions.forEach(action -> lines.add("  - " + action));
        }
        if (!failedOrBlockedActions.isEmpty()) {
            lines.add("- Failed or blocked actions:");
            failedOrBlockedActions.forEach(action -> lines.add("  - " + action));
        }
        if (!pendingDecisions.isEmpty()) {
            lines.add("- Pending decisions:");
            pendingDecisions.forEach(action -> lines.add("  - " + action));
        }
        lines.add("- Do not assume any action was completed unless listed under Successful external actions.");
        lines.add(
                "- Successful external actions only describe historical actions, not evidence for the current turn. Unless the user explicitly asks to continue from the previous result, do not treat them as proof that the current request is already completed.");
        return String.join("\n", lines);
    }

    public int countMemoryItems(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            return 0;
        }
        int messageCount = conversationMessageMapper
                .selectMessagesAfterSequence(sessionId, null)
                .size();
        int eventCount = conversationEventMapper
                .selectAfterSequence(sessionId, null, memoryEnabledEventTypes())
                .size();
        return messageCount + eventCount;
    }

    public ConversationEvent findLatestSummary(Long sessionId) {
        return conversationEventMapper.selectLatestSummary(sessionId);
    }

    public List<MemoryItem> listMemoryItemsAfterSummary(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            return List.of();
        }
        ConversationEvent latestSummary = findLatestSummary(sessionId);
        int summaryMessageSequence = extractCompressedUntilSequence(latestSummary, "compressedUntilMessageSequence");
        int summaryEventSequence = extractCompressedUntilSequence(latestSummary, "compressedUntilEventSequence");
        List<MemoryItem> items = new ArrayList<>();
        for (ConversationMessage message :
                conversationMessageMapper.selectMessagesAfterSequence(sessionId, summaryMessageSequence)) {
            MemoryItem item = toMemoryItem(message);
            if (item != null) {
                items.add(item);
            }
        }
        for (ConversationEvent event : conversationEventMapper.selectAfterSequence(
                sessionId, summaryEventSequence, memorySummaryEventTypes())) {
            MemoryItem item = toMemoryItem(event);
            if (item != null) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparing(MemoryItem::createdAt).thenComparing(MemoryItem::orderKey));
        return items;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBySessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            return;
        }
        conversationEventMapper.deleteBySessionId(sessionId);
    }

    private ConversationSession resolveSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        return conversationSessionMapper.selectBySessionCodeGlobal(sessionId.trim());
    }

    private List<String> memoryEnabledEventTypes() {
        return List.of(
                "ROUTE_SELECTED",
                "TOOL_FINISHED",
                "TOOL_FAILED",
                "ARTIFACT_READY",
                "PERSONAL_AGENT_MODE_RESOLVED",
                "EXECUTION_PLAN_CREATED",
                "EXECUTION_PRECHECK_COMPLETED",
                "EXECUTION_PRECHECK_BLOCKED",
                "EXECUTION_CONFIRMATION_REQUIRED",
                "EXECUTION_STEP_STARTED",
                "EXECUTION_STEP_COMPLETED",
                "EXECUTION_STEP_FAILED",
                "SUMMARY_SNAPSHOT");
    }

    private List<String> promptVisibleEventTypes() {
        return List.of(
                "ROUTE_SELECTED",
                "PERSONAL_AGENT_MODE_RESOLVED",
                "EXECUTION_PRECHECK_BLOCKED",
                "EXECUTION_CONFIRMATION_REQUIRED",
                "SUMMARY_SNAPSHOT");
    }

    private List<String> executionStateEventTypes() {
        return List.of(
                "TOOL_FINISHED",
                "TOOL_FAILED",
                "ARTIFACT_READY",
                "EXECUTION_PRECHECK_BLOCKED",
                "EXECUTION_CONFIRMATION_REQUIRED",
                "EXECUTION_STEP_COMPLETED",
                "EXECUTION_STEP_FAILED",
                "MESSAGE_FAILED",
                "SUMMARY_SNAPSHOT");
    }

    private List<String> memorySummaryEventTypes() {
        List<String> values = new ArrayList<>();
        for (String eventType : memoryEnabledEventTypes()) {
            if (eventTypeConfigRegistry.participatesInSummary(eventType)) {
                values.add(eventType);
            }
        }
        return values;
    }

    private MemoryItem toMemoryItem(ConversationMessage message) {
        if (!shouldIncludeHistoryMessage(message)) {
            return null;
        }
        String role = normalizeText(message.getRole()).toUpperCase();
        String sourceText =
                "ASSISTANT".equals(role) ? sanitizeAssistantHistoryText(message.getContent()) : message.getContent();
        String normalizedText = shrinkForHistory(sourceText, resolveHistoryCharLimit(role, false));
        if (!StringUtils.hasText(normalizedText)) {
            return null;
        }
        return new MemoryItem(
                "MESSAGE",
                role,
                normalizedText,
                message.getCreatedAt() == null ? new Date(0) : message.getCreatedAt(),
                "M-" + (message.getSequenceNo() == null ? 0 : message.getSequenceNo()),
                message.getSequenceNo(),
                null,
                estimateTokens(normalizedText));
    }

    static boolean shouldIncludeHistoryMessage(ConversationMessage message) {
        if (message == null || !StringUtils.hasText(message.getContent())) {
            return false;
        }
        String role = StringUtils.hasText(message.getRole())
                ? message.getRole().trim().toUpperCase()
                : "";
        if (!List.of("USER", "ASSISTANT", "SYSTEM").contains(role)) {
            return false;
        }
        if (!"ASSISTANT".equals(role)) {
            return true;
        }
        String status = StringUtils.hasText(message.getStatus())
                ? message.getStatus().trim().toUpperCase()
                : "";
        return !List.of("FAILED", "CANCELLED").contains(status);
    }

    private MemoryItem toMemoryItem(ConversationEvent event) {
        if (event == null || !StringUtils.hasText(event.getEventType())) {
            return null;
        }
        String memoryText = eventTypeConfigRegistry.resolveMemoryText(
                event.getEventType(), event.getSummaryText(), event.getPayloadJson());
        if (!StringUtils.hasText(memoryText)) {
            return null;
        }
        String normalizedText = shrinkForHistory(
                "[运行事实] " + memoryText,
                resolveHistoryCharLimit("SYSTEM", "SUMMARY_SNAPSHOT".equals(event.getEventType())));
        if (!StringUtils.hasText(normalizedText)) {
            return null;
        }
        return new MemoryItem(
                "EVENT",
                "SYSTEM",
                normalizedText,
                event.getCreatedAt() == null ? new Date(0) : event.getCreatedAt(),
                "E-" + (event.getSequenceNo() == null ? 0 : event.getSequenceNo()),
                null,
                event.getSequenceNo(),
                estimateTokens(normalizedText));
    }

    private MemoryItem toSummaryMemoryItem(ConversationEvent event) {
        if (event == null || !StringUtils.hasText(event.getSummaryText())) {
            return null;
        }
        String normalizedText = shrinkForHistory(event.getSummaryText(), resolveHistoryCharLimit("SYSTEM", true));
        if (!StringUtils.hasText(normalizedText)) {
            return null;
        }
        return new MemoryItem(
                "SUMMARY",
                "SYSTEM",
                normalizedText,
                event.getCreatedAt() == null ? new Date(0) : event.getCreatedAt(),
                "S-" + (event.getSequenceNo() == null ? 0 : event.getSequenceNo()),
                null,
                event.getSequenceNo(),
                estimateTokens(normalizedText));
    }

    private Message toSpringMessage(MemoryItem item) {
        if (item == null || !StringUtils.hasText(item.text())) {
            return null;
        }
        return switch (item.role()) {
            case "ASSISTANT" -> new AssistantMessage(item.text());
            case "SYSTEM" -> new SystemMessage(item.text());
            default -> new UserMessage(item.text());
        };
    }

    static String sanitizeAssistantHistoryText(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.trim();
        Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(normalized);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String label = matcher.group(1) == null ? "" : matcher.group(1).trim();
            String url = matcher.group(2) == null ? "" : matcher.group(2).trim();
            if (isArtifactUrl(url)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(label));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(buffer);
        normalized = INLINE_ARTIFACT_URL_PATTERN.matcher(buffer.toString()).replaceAll("");
        normalized = normalized.replaceAll("[ \\t]+\\n", "\n");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        normalized = normalized.trim();
        List<String> keptParagraphs = new ArrayList<>();
        for (String paragraph : ASSISTANT_HISTORY_SPLIT_PATTERN.split(normalized)) {
            String cleanedParagraph = shrinkPlainText(paragraph, 240);
            if (!StringUtils.hasText(cleanedParagraph) || isAssistantExecutionChatter(cleanedParagraph)) {
                continue;
            }
            keptParagraphs.add(cleanedParagraph);
        }
        if (!keptParagraphs.isEmpty()) {
            return String.join("\n\n", keptParagraphs);
        }
        String fallback = extractAssistantFallbackParagraph(normalized);
        return shrinkPlainText(fallback, 240);
    }

    static List<MemoryItem> collapseSystemHistoryForPrompt(List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<MemoryItem> systemItems = new ArrayList<>();
        List<MemoryItem> collapsed = new ArrayList<>();
        boolean systemSummaryInserted = false;
        for (MemoryItem item : items) {
            if (item == null) {
                continue;
            }
            if ("SYSTEM".equalsIgnoreCase(item.role())) {
                systemItems.add(item);
                if (!systemSummaryInserted) {
                    collapsed.add(buildPromptSystemSummary(systemItems));
                    systemSummaryInserted = true;
                }
                continue;
            }
            collapsed.add(item);
        }
        if (!systemItems.isEmpty()) {
            for (int i = 0; i < collapsed.size(); i++) {
                MemoryItem current = collapsed.get(i);
                if (current != null && "SYSTEM".equalsIgnoreCase(current.role())) {
                    collapsed.set(i, buildPromptSystemSummary(systemItems));
                    break;
                }
            }
        }
        return collapsed;
    }

    static MemoryItem buildPromptSystemSummary(List<MemoryItem> systemItems) {
        if (systemItems == null || systemItems.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        lines.add("[历史运行摘要]");
        lines.add("以下仅保留少量历史状态供参考，不能视为当前轮已经完成；若当前问题需要新查询或新产物，仍需重新执行。");
        int included = 0;
        for (MemoryItem item : systemItems) {
            if (item == null || !StringUtils.hasText(item.text()) || included >= PROMPT_HISTORY_SYSTEM_EVENT_LIMIT) {
                continue;
            }
            lines.add("- " + shrinkPlainText(item.text(), 80));
            included++;
        }
        if (systemItems.size() > included) {
            lines.add("- 其余历史运行细节已省略。");
        }
        String text = shrinkPlainText(String.join("\n", lines), PROMPT_HISTORY_SYSTEM_SUMMARY_CHAR_LIMIT);
        MemoryItem seed = systemItems.get(0);
        return new MemoryItem(
                "SYSTEM_PROMPT_SUMMARY",
                "SYSTEM",
                text,
                seed.createdAt(),
                seed.orderKey(),
                null,
                seed.eventSequenceNo(),
                estimateTokensStatic(text));
    }

    private static boolean isArtifactUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String normalized = url.trim().toLowerCase();
        return normalized.startsWith("artifact://")
                || normalized.contains("/api/files/artifacts/")
                || normalized.contains("/download?filename=");
    }

    private static boolean isAssistantExecutionChatter(String paragraph) {
        String normalized = normalizePlainText(paragraph);
        if (!StringUtils.hasText(normalized)) {
            return true;
        }
        String compact = normalized.replace(" ", "");
        for (String prefix : ASSISTANT_EXECUTION_PREFIXES) {
            if (compact.startsWith(prefix) && containsAnyIgnoreCase(compact, ASSISTANT_EXECUTION_HINTS)) {
                return true;
            }
        }
        return compact.startsWith("正在");
    }

    private static String extractAssistantFallbackParagraph(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String[] paragraphs = ASSISTANT_HISTORY_SPLIT_PATTERN.split(content.trim());
        for (int i = paragraphs.length - 1; i >= 0; i--) {
            String candidate = shrinkPlainText(paragraphs[i], 240);
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return content.trim();
    }

    private int extractCompressedUntilSequence(ConversationEvent summary, String fieldName) {
        if (summary == null || !StringUtils.hasText(summary.getPayloadJson()) || !StringUtils.hasText(fieldName)) {
            return 0;
        }
        try {
            Map<String, Object> payload = JSON.parseObject(summary.getPayloadJson());
            Object value = payload == null ? null : payload.get(fieldName);
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean isLatestSummaryEvent(ConversationEvent latestSummary, ConversationEvent event) {
        if (latestSummary == null || event == null || latestSummary.getId() == null || event.getId() == null) {
            return false;
        }
        return latestSummary.getId().equals(event.getId());
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeText(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private List<MemoryItem> buildHistoryWindow(List<MemoryItem> items, int maxMessages) {
        if (items == null || items.isEmpty() || maxMessages <= 0) {
            return List.of();
        }
        int maxHistoryTokens = Math.max(0, chatContextProperties.getMaxHistoryTokens());
        MemoryItem summaryItem = null;
        List<MemoryItem> regularItems = new ArrayList<>();
        for (MemoryItem item : items) {
            if (item == null) {
                continue;
            }
            if (item.isSummarySnapshot()) {
                summaryItem = item;
                continue;
            }
            regularItems.add(item);
        }

        List<MemoryItem> selected = new ArrayList<>();
        int remainingSlots = maxMessages;
        int remainingTokens = maxHistoryTokens;
        if (summaryItem != null && remainingSlots > 0) {
            selected.add(summaryItem);
            remainingSlots--;
            if (remainingTokens > 0) {
                remainingTokens -= summaryItem.estimatedTokens();
            }
            if (remainingTokens <= 0 && maxHistoryTokens > 0) {
                return selected;
            }
        }

        LinkedList<MemoryItem> recentItems = new LinkedList<>();
        for (int i = regularItems.size() - 1; i >= 0 && remainingSlots > 0; i--) {
            MemoryItem item = regularItems.get(i);
            int estimatedTokens = Math.max(1, item.estimatedTokens());
            boolean fitsBudget = remainingTokens <= 0 || estimatedTokens <= remainingTokens;
            if (!fitsBudget && !recentItems.isEmpty()) {
                continue;
            }
            recentItems.addFirst(item);
            remainingSlots--;
            if (remainingTokens > 0) {
                remainingTokens -= estimatedTokens;
            }
        }
        selected.addAll(recentItems);
        return selected;
    }

    private int sumEstimatedTokens(List<MemoryItem> items) {
        int total = 0;
        if (items == null || items.isEmpty()) {
            return total;
        }
        for (MemoryItem item : items) {
            if (item == null) {
                continue;
            }
            total += Math.max(0, item.estimatedTokens());
        }
        return total;
    }

    private int resolveHistoryCharLimit(String role, boolean summarySnapshot) {
        if (summarySnapshot) {
            return Math.max(200, chatContextProperties.getMaxSummaryMessageChars());
        }
        return switch (normalizeText(role).toUpperCase()) {
            case "USER" -> Math.max(200, chatContextProperties.getMaxUserMessageChars());
            case "ASSISTANT" -> Math.max(200, chatContextProperties.getMaxAssistantMessageChars());
            default -> Math.max(120, chatContextProperties.getMaxSystemMessageChars());
        };
    }

    private String shrinkForHistory(String value, int maxChars) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized) || maxChars <= 0 || normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "…";
    }

    private int estimateTokens(String text) {
        return estimateTokensStatic(text);
    }

    private static int estimateTokensStatic(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int asciiVisibleChars = 0;
        int otherChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch <= 0x7F) {
                asciiVisibleChars++;
                continue;
            }
            otherChars++;
        }
        return otherChars + (int) Math.ceil(asciiVisibleChars / 4.0d);
    }

    private static String shrinkPlainText(String value, int maxChars) {
        if (!StringUtils.hasText(value) || maxChars <= 0) {
            return "";
        }
        String normalized = normalizePlainText(value);
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "…";
    }

    private static String normalizePlainText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replace("\r", "").replaceAll("\\n{2,}", "\n");
    }

    private static boolean containsAnyIgnoreCase(String text, List<String> fragments) {
        if (!StringUtils.hasText(text) || fragments == null || fragments.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String fragment : fragments) {
            if (StringUtils.hasText(fragment) && lower.contains(fragment.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void addUnique(List<String> values, String value, int limit) {
        if (values == null || !StringUtils.hasText(value) || values.contains(value) || values.size() >= limit) {
            return;
        }
        values.add(value);
    }

    public record MemoryItem(
            String sourceType,
            String role,
            String text,
            Date createdAt,
            String orderKey,
            Integer messageSequenceNo,
            Integer eventSequenceNo,
            int estimatedTokens) {

        public boolean isSummarySnapshot() {
            return "SUMMARY".equals(sourceType);
        }
    }
}
