package lingzhou.agent.backend.capability.agentruntime.capabilities;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.chat.service.ConversationMessageUsagePayload;
import lingzhou.agent.backend.business.chat.service.ConversationRunUsageService;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilityStatus;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeTokenUsageAccumulator;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

@Component
public class TokenUsageCapabilityAdapter extends AbstractAgentRuntimeCapability {

    private final ConversationRunUsageService conversationRunUsageService;

    public TokenUsageCapabilityAdapter(ConversationRunUsageService conversationRunUsageService) {
        super(RuntimeCapabilitySlot.TOKEN_USAGE, "token-usage", RuntimeCapabilityStatus.ACTIVE);
        this.conversationRunUsageService = conversationRunUsageService;
    }

    public RuntimeTokenUsageAccumulator createAccumulator(
            ModelRuntimeConfigResolver.ResolvedChatModelConfig chatConfig, long runStartedAtMillis) {
        return new RuntimeTokenUsageAccumulator(
                runStartedAtMillis,
                chatConfig == null ? null : chatConfig.modelId(),
                chatConfig == null ? null : chatConfig.provider(),
                chatConfig == null ? null : chatConfig.model(),
                chatConfig == null ? null : chatConfig.adapterType());
    }

    public void ensureRunStarted(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            ModelRuntimeConfigResolver.ResolvedChatModelConfig chatConfig,
            long startedAtMillis) {
        conversationRunUsageService.ensureRunningRecord(
                context,
                prepared,
                chatConfig == null ? null : chatConfig.modelId(),
                chatConfig == null ? null : chatConfig.provider(),
                chatConfig == null ? null : chatConfig.model(),
                chatConfig == null ? null : chatConfig.adapterType(),
                startedAtMillis);
    }

    public void recordResponse(RuntimeTokenUsageAccumulator accumulator, ChatResponse chatResponse) {
        if (accumulator == null) {
            return;
        }
        accumulator.recordResponse(chatResponse);
    }

    public void recordToolEvent(RuntimeTokenUsageAccumulator accumulator, String eventType, String payload) {
        if (accumulator == null) {
            return;
        }
        accumulator.recordToolEvent(eventType, payload);
    }

    public boolean completeCurrentCall(
            RuntimeTokenUsageAccumulator accumulator, String status, long completedAtMillis, int outputChars) {
        if (accumulator == null) {
            return false;
        }
        return accumulator.completeCurrentCall(status, completedAtMillis, outputChars);
    }

    public RuntimeRunUsageSnapshot snapshot(
            RuntimeTokenUsageAccumulator accumulator, String runStatus, long completedAtMillis) {
        if (accumulator == null) {
            return null;
        }
        return accumulator.snapshot(runStatus, completedAtMillis);
    }

    public ConversationMessageUsagePayload toMessageUsagePayload(RuntimeRunUsageSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new ConversationMessageUsagePayload(
                snapshot.promptTokens(),
                snapshot.completionTokens(),
                snapshot.totalTokens(),
                snapshot.usageAvailable(),
                snapshot.llmCallCount(),
                snapshot.toolCallCount(),
                snapshot.modelId(),
                snapshot.modelProvider(),
                snapshot.modelName(),
                snapshot.adapterType(),
                JSON.toJSONString(buildUsageSummary(snapshot)));
    }

    public void finalizeRun(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            RuntimeRunUsageSnapshot snapshot) {
        conversationRunUsageService.finalizeRun(context, prepared, snapshot);
    }

    public void persistRun(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            RuntimeRunUsageSnapshot snapshot) {
        conversationRunUsageService.persistRun(context, prepared, snapshot);
    }

    private Map<String, Object> buildUsageSummary(RuntimeRunUsageSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runStatus", snapshot.runStatus());
        payload.put("usageAvailable", snapshot.usageAvailable());
        payload.put("promptTokens", snapshot.promptTokens());
        payload.put("completionTokens", snapshot.completionTokens());
        payload.put("totalTokens", snapshot.totalTokens());
        payload.put("llmCallCount", snapshot.llmCallCount());
        payload.put("toolCallCount", snapshot.toolCallCount());
        payload.put("durationMs", snapshot.durationMs());
        payload.put("startedAtMillis", snapshot.startedAtMillis());
        payload.put("completedAtMillis", snapshot.completedAtMillis());
        payload.put("modelId", snapshot.modelId());
        payload.put("modelProvider", snapshot.modelProvider());
        payload.put("modelName", snapshot.modelName());
        payload.put("adapterType", snapshot.adapterType());
        payload.put("calls", snapshot.modelCalls());
        return payload;
    }
}
