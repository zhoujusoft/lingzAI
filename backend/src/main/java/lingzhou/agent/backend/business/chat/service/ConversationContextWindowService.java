package lingzhou.agent.backend.business.chat.service;

import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lingzhou.agent.backend.app.ChatContextProperties;
import lingzhou.agent.backend.business.chat.domain.ConversationEvent;
import lingzhou.agent.backend.capability.agentruntime.prompt.PromptEngineeringService;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ConversationContextWindowService {

    private final ConversationEventService conversationEventService;
    private final ChatContextProperties chatContextProperties;
    private final ModelRuntimeClientFactory modelRuntimeClientFactory;
    private final PromptEngineeringService promptEngineeringService;

    public ConversationContextWindowService(
            ConversationEventService conversationEventService,
            ChatContextProperties chatContextProperties,
            ModelRuntimeClientFactory modelRuntimeClientFactory,
            PromptEngineeringService promptEngineeringService) {
        this.conversationEventService = conversationEventService;
        this.chatContextProperties = chatContextProperties;
        this.modelRuntimeClientFactory = modelRuntimeClientFactory;
        this.promptEngineeringService = promptEngineeringService;
    }

    public void compactIfNeeded(ConversationHistoryService.ConversationContext context) {
        if (context == null || context.sessionId() == null) {
            return;
        }
        int totalItems = conversationEventService.countMemoryItems(context.sessionId());
        int threshold = Math.max(10, chatContextProperties.getCompressThreshold());
        if (totalItems <= threshold) {
            return;
        }

        ConversationEvent latestSummary = conversationEventService.findLatestSummary(context.sessionId());
        if (isInSummaryCooldown(latestSummary)) {
            return;
        }

        List<ConversationEventService.MemoryItem> allItems =
                new ArrayList<>(conversationEventService.listMemoryItemsAfterSummary(context.sessionId()));
        allItems.sort(Comparator.comparing(ConversationEventService.MemoryItem::createdAt)
                .thenComparing(ConversationEventService.MemoryItem::orderKey));
        int preserveRecent = Math.max(10, chatContextProperties.getPreserveRecentMessages());
        if (allItems.size() <= preserveRecent) {
            return;
        }

        int compressCount = allItems.size() - preserveRecent;
        List<ConversationEventService.MemoryItem> candidates = new ArrayList<>(allItems.subList(0, compressCount));
        if (candidates.size() < Math.max(4, chatContextProperties.getMinCompressMessages())) {
            return;
        }

        String previousSummary = latestSummary == null ? "" : latestSummary.getSummaryText();
        String summary = buildSummary(candidates, previousSummary);
        if (!StringUtils.hasText(summary)) {
            return;
        }

        int lastMessageSequence = extractMaxMessageSequence(candidates);
        int lastEventSequence = extractMaxEventSequence(candidates);
        conversationEventService.appendSummaryMessage(
                context.sessionId(),
                context.sessionCode(),
                context.createUserId(),
                summary,
                lastMessageSequence,
                lastEventSequence,
                candidates.size(),
                extractSummaryVersion(latestSummary) + 1);
    }

    private String buildSummary(List<ConversationEventService.MemoryItem> items, String previousSummary) {
        String llmSummary = buildLlmSummary(items, previousSummary);
        if (StringUtils.hasText(llmSummary)) {
            return llmSummary;
        }
        return buildRuleSummary(items);
    }

    private String buildLlmSummary(List<ConversationEventService.MemoryItem> items, String previousSummary) {
        String transcript = serializeMessages(items);
        if (!StringUtils.hasText(transcript)) {
            return "";
        }
        String userPrompt = promptEngineeringService.buildConversationSummaryUserPrompt(previousSummary, transcript);
        try {
            String content = modelRuntimeClientFactory
                    .createChatBundle()
                    .chatClient()
                    .prompt()
                    .system(promptEngineeringService.buildConversationSummarySystemPrompt())
                    .user(userPrompt)
                    .call()
                    .content();
            return shrink(content, 4000);
        } catch (Exception ex) {
            log.warn("生成 LLM 上下文摘要失败，回退规则摘要：sessionSummaryError={}", ex.getMessage());
            return "";
        }
    }

    private String buildRuleSummary(List<ConversationEventService.MemoryItem> items) {
        List<String> lines = new ArrayList<>();
        lines.add("[历史上下文摘要]");
        lines.add("以下内容是较早对话的压缩摘要，请在继续回答时继承这些上下文，避免重复。");
        int userCount = 0;
        int assistantCount = 0;
        int eventCount = 0;
        for (ConversationEventService.MemoryItem item : items) {
            if (item == null || !StringUtils.hasText(item.text())) {
                continue;
            }
            String prefix =
                    switch (normalizeRole(item.role())) {
                        case "assistant" -> {
                            assistantCount++;
                            yield "[助手]";
                        }
                        case "system" -> {
                            eventCount++;
                            yield "[事件]";
                        }
                        default -> {
                            userCount++;
                            yield "[用户]";
                        }
                    };
            lines.add(prefix + " " + shrink(item.text(), 180));
        }
        lines.add("");
        lines.add("统计：用户消息 " + userCount + " 条，助手消息 " + assistantCount + " 条，关键事件 " + eventCount + " 条。");
        return shrink(String.join("\n", lines), 4000);
    }

    private String serializeMessages(List<ConversationEventService.MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (ConversationEventService.MemoryItem item : items) {
            if (item == null || !StringUtils.hasText(item.text())) {
                continue;
            }
            String prefix =
                    switch (normalizeRole(item.role())) {
                        case "assistant" -> "[助手]";
                        case "system" -> "[事件]";
                        default -> "[用户]";
                    };
            lines.add(prefix + " " + shrink(item.text(), 500));
        }
        return String.join("\n", lines);
    }

    private boolean isInSummaryCooldown(ConversationEvent latestSummary) {
        if (latestSummary == null || latestSummary.getCreatedAt() == null) {
            return false;
        }
        int cooldownSeconds = Math.max(0, chatContextProperties.getSummaryCooldownSeconds());
        if (cooldownSeconds <= 0) {
            return false;
        }
        long cooldownMillis = cooldownSeconds * 1000L;
        return System.currentTimeMillis() - latestSummary.getCreatedAt().getTime() < cooldownMillis;
    }

    private int extractSummaryVersion(ConversationEvent summary) {
        if (summary == null || !StringUtils.hasText(summary.getPayloadJson())) {
            return 0;
        }
        try {
            var payload = JSON.parseObject(summary.getPayloadJson());
            Object value = payload == null ? null : payload.get("summaryVersion");
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int extractMaxMessageSequence(List<ConversationEventService.MemoryItem> items) {
        int max = 0;
        for (ConversationEventService.MemoryItem item : items) {
            if (item == null || item.messageSequenceNo() == null) {
                continue;
            }
            max = Math.max(max, item.messageSequenceNo());
        }
        return max;
    }

    private int extractMaxEventSequence(List<ConversationEventService.MemoryItem> items) {
        int max = 0;
        for (ConversationEventService.MemoryItem item : items) {
            if (item == null || item.eventSequenceNo() == null) {
                continue;
            }
            max = Math.max(max, item.eventSequenceNo());
        }
        return max;
    }

    private String normalizeRole(String role) {
        return StringUtils.hasText(role) ? role.trim().toLowerCase() : "";
    }

    private String shrink(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replace("\r", "").replace("\n", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "…";
    }
}
