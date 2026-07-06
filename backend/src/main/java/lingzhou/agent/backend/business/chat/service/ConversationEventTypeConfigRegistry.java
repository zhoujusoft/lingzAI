package lingzhou.agent.backend.business.chat.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationEventMemorySource;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationEventSummaryMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ConversationEventTypeConfigRegistry {

    private final Map<String, EventTypeConfig> configs;

    public ConversationEventTypeConfigRegistry() {
        Map<String, EventTypeConfig> values = new LinkedHashMap<>();
        values.put(
                "USER_MESSAGE_CREATED",
                new EventTypeConfig(
                        false,
                        ConversationEventSummaryMode.NONE,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        false,
                        0));
        values.put(
                "ASSISTANT_MESSAGE_STARTED",
                new EventTypeConfig(
                        false,
                        ConversationEventSummaryMode.NONE,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        false,
                        0));
        values.put(
                "ROUTE_SELECTED",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.OPTIONAL,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        80));
        values.put(
                "TOOL_STARTED",
                new EventTypeConfig(
                        false,
                        ConversationEventSummaryMode.NONE,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        false,
                        0));
        values.put(
                "TOOL_FINISHED",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.REQUIRED,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        100));
        values.put(
                "TOOL_FAILED",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.REQUIRED,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        100));
        values.put(
                "ARTIFACT_READY",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.REQUIRED,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        90));
        values.put(
                "MESSAGE_COMPLETED",
                new EventTypeConfig(
                        false,
                        ConversationEventSummaryMode.NONE,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        false,
                        0));
        values.put(
                "MESSAGE_FAILED",
                new EventTypeConfig(
                        false,
                        ConversationEventSummaryMode.OPTIONAL,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        false,
                        20));
        values.put(
                "MESSAGE_CANCELLED",
                new EventTypeConfig(
                        false,
                        ConversationEventSummaryMode.OPTIONAL,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        false,
                        20));
        values.put(
                "MODEL_CALL_COMPLETED",
                new EventTypeConfig(
                        false,
                        ConversationEventSummaryMode.NONE,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        false,
                        0));
        values.put(
                "MODEL_CALL_FAILED",
                new EventTypeConfig(
                        false,
                        ConversationEventSummaryMode.NONE,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        false,
                        0));
        values.put(
                "MODEL_CALL_CANCELLED",
                new EventTypeConfig(
                        false,
                        ConversationEventSummaryMode.NONE,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        false,
                        0));
        values.put(
                "PERSONAL_AGENT_MODE_RESOLVED",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.OPTIONAL,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        60));
        values.put(
                "EXECUTION_PLAN_CREATED",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.OPTIONAL,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        80));
        values.put(
                "EXECUTION_PRECHECK_COMPLETED",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.OPTIONAL,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        80));
        values.put(
                "EXECUTION_PRECHECK_BLOCKED",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.REQUIRED,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        100));
        values.put(
                "EXECUTION_CONFIRMATION_REQUIRED",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.REQUIRED,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        100));
        values.put(
                "EXECUTION_STEP_STARTED",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.OPTIONAL,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        80));
        values.put(
                "EXECUTION_STEP_COMPLETED",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.OPTIONAL,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        80));
        values.put(
                "EXECUTION_STEP_FAILED",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.REQUIRED,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        true,
                        100));
        values.put(
                "RUN_USAGE_FINALIZED",
                new EventTypeConfig(
                        false,
                        ConversationEventSummaryMode.NONE,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        false,
                        0));
        values.put(
                "SUMMARY_SNAPSHOT",
                new EventTypeConfig(
                        true,
                        ConversationEventSummaryMode.REQUIRED,
                        ConversationEventMemorySource.SUMMARY_TEXT,
                        false,
                        120));
        this.configs = Map.copyOf(values);
    }

    public EventTypeConfig get(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return EventTypeConfig.disabled();
        }
        return configs.getOrDefault(eventType.trim().toUpperCase(), EventTypeConfig.disabled());
    }

    public boolean isMemoryEnabled(String eventType) {
        return get(eventType).memoryEnabled();
    }

    public boolean participatesInSummary(String eventType) {
        return get(eventType).summaryParticipation();
    }

    public String resolveMemoryText(String eventType, String summaryText, String payloadJson) {
        EventTypeConfig config = get(eventType);
        if (!config.memoryEnabled()) {
            return "";
        }
        String normalizedSummary = normalize(summaryText);
        if (config.summaryMode() == ConversationEventSummaryMode.REQUIRED && !StringUtils.hasText(normalizedSummary)) {
            return "";
        }
        if (config.memorySource() == ConversationEventMemorySource.SUMMARY_TEXT) {
            return normalizedSummary;
        }
        if (config.memorySource() == ConversationEventMemorySource.PAYLOAD_JSON) {
            return deriveFromPayload(payloadJson);
        }
        if (StringUtils.hasText(normalizedSummary)) {
            return normalizedSummary;
        }
        return deriveFromPayload(payloadJson);
    }

    private String deriveFromPayload(String payloadJson) {
        String normalized = normalize(payloadJson);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        try {
            Object parsed = JSON.parse(normalized);
            if (parsed instanceof JSONObject jsonObject) {
                Object summary = jsonObject.get("summaryText");
                if (summary != null && StringUtils.hasText(String.valueOf(summary))) {
                    return String.valueOf(summary).trim();
                }
            }
        } catch (Exception ignored) {
            // Keep plain text fallback.
        }
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "…";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public record EventTypeConfig(
            boolean memoryEnabled,
            ConversationEventSummaryMode summaryMode,
            ConversationEventMemorySource memorySource,
            boolean summaryParticipation,
            int defaultPriority) {

        public static EventTypeConfig disabled() {
            return new EventTypeConfig(
                    false, ConversationEventSummaryMode.NONE, ConversationEventMemorySource.SUMMARY_TEXT, false, 0);
        }
    }
}
