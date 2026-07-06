package lingzhou.agent.backend.capability.agentruntime.usage;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

public class RuntimeTokenUsageAccumulator {

    private final long runStartedAtMillis;
    private final Long modelId;
    private final String modelProvider;
    private final String modelName;
    private final String adapterType;
    private final List<RuntimeModelCallUsage> completedCalls = new ArrayList<>();
    private final Set<String> toolCallIds = new LinkedHashSet<>();
    private int anonymousToolCallCount;
    private int nextCallNo = 1;
    private ModelCallState currentCall;

    public RuntimeTokenUsageAccumulator(
            long runStartedAtMillis, Long modelId, String modelProvider, String modelName, String adapterType) {
        this.runStartedAtMillis = runStartedAtMillis;
        this.modelId = modelId;
        this.modelProvider = normalizeText(modelProvider);
        this.modelName = normalizeText(modelName);
        this.adapterType = normalizeText(adapterType);
    }

    public synchronized void ensureCurrentCall(long startedAtMillis) {
        if (currentCall == null) {
            currentCall = new ModelCallState(nextCallNo++, startedAtMillis);
        }
    }

    public synchronized boolean hasCurrentCall() {
        return currentCall != null;
    }

    public synchronized void recordResponse(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        if (currentCall == null) {
            return;
        }
        if (usage.getPromptTokens() == null && usage.getCompletionTokens() == null && usage.getTotalTokens() == null) {
            return;
        }
        currentCall.usageAvailable = true;
        currentCall.promptTokens = sanitizeTokenValue(usage.getPromptTokens());
        currentCall.completionTokens = sanitizeTokenValue(usage.getCompletionTokens());
        currentCall.totalTokens = resolveTotalTokens(
                sanitizeTokenValue(usage.getTotalTokens()), currentCall.promptTokens, currentCall.completionTokens);
    }

    public synchronized boolean completeCurrentCall(String status, long completedAtMillis, int outputChars) {
        if (currentCall == null) {
            return false;
        }
        completedCalls.add(currentCall.toUsage(status, completedAtMillis, outputChars));
        currentCall = null;
        return true;
    }

    public synchronized void recordToolEvent(String eventType, String payload) {
        if (!"tool".equals(eventType) || !StringUtils.hasText(payload)) {
            return;
        }
        String toolCallId = extractToolCallId(payload);
        if (StringUtils.hasText(toolCallId)) {
            toolCallIds.add(toolCallId);
            return;
        }
        anonymousToolCallCount++;
    }

    public synchronized int currentToolCallCount() {
        return toolCallIds.size() + anonymousToolCallCount;
    }

    public synchronized RuntimeRunUsageSnapshot snapshot(String runStatus, long completedAtMillis) {
        List<RuntimeModelCallUsage> modelCalls = List.copyOf(completedCalls);
        int llmCallCount = modelCalls.size();
        int availableCallCount = 0;
        int promptSum = 0;
        int completionSum = 0;
        int totalSum = 0;
        boolean allUsageAvailable = llmCallCount > 0;
        for (RuntimeModelCallUsage call : modelCalls) {
            if (call.usageAvailable()) {
                availableCallCount++;
                promptSum += safeInt(call.promptTokens());
                completionSum += safeInt(call.completionTokens());
                totalSum += safeInt(call.totalTokens());
            } else {
                allUsageAvailable = false;
            }
        }
        Integer promptTokens = availableCallCount == 0 ? null : promptSum;
        Integer completionTokens = availableCallCount == 0 ? null : completionSum;
        Integer totalTokens = availableCallCount == 0 ? null : totalSum;
        long durationMs = Math.max(0L, completedAtMillis - runStartedAtMillis);
        return new RuntimeRunUsageSnapshot(
                normalizeText(runStatus),
                allUsageAvailable && availableCallCount > 0,
                promptTokens,
                completionTokens,
                totalTokens,
                llmCallCount,
                toolCallIds.size() + anonymousToolCallCount,
                durationMs,
                runStartedAtMillis,
                completedAtMillis,
                modelId,
                modelProvider,
                modelName,
                adapterType,
                modelCalls);
    }

    private Integer sanitizeTokenValue(Integer value) {
        if (value == null) {
            return null;
        }
        return Math.max(0, value);
    }

    private Integer resolveTotalTokens(Integer totalTokens, Integer promptTokens, Integer completionTokens) {
        if (totalTokens != null) {
            return totalTokens;
        }
        if (promptTokens == null && completionTokens == null) {
            return null;
        }
        return safeInt(promptTokens) + safeInt(completionTokens);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String extractToolCallId(String payload) {
        try {
            Map<String, Object> wrapper = JSON.parseObject(payload, new TypeReference<Map<String, Object>>() {});
            Object content = wrapper == null ? null : wrapper.get("content");
            if (content instanceof Map<?, ?> contentMap) {
                Object idValue = contentMap.get("id");
                return normalizeText(idValue == null ? null : String.valueOf(idValue));
            }
        } catch (Exception ignored) {
            // Ignore payload parse failures and keep anonymous fallback.
        }
        return "";
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static final class ModelCallState {

        private final int callNo;
        private final long startedAtMillis;
        private boolean usageAvailable;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;

        private ModelCallState(int callNo, long startedAtMillis) {
            this.callNo = callNo;
            this.startedAtMillis = startedAtMillis;
        }

        private RuntimeModelCallUsage toUsage(String status, long completedAtMillis, int outputChars) {
            long finishedAt = Math.max(completedAtMillis, startedAtMillis);
            return new RuntimeModelCallUsage(
                    callNo,
                    StringUtils.hasText(status) ? status.trim().toUpperCase() : "COMPLETED",
                    usageAvailable,
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    Math.max(0, outputChars),
                    startedAtMillis,
                    finishedAt,
                    Math.max(0L, finishedAt - startedAtMillis));
        }
    }
}
