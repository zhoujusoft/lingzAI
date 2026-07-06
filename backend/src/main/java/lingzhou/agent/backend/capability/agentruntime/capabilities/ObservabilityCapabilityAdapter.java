package lingzhou.agent.backend.capability.agentruntime.capabilities;

import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntime;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeExecutionContext;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilitySlot;
import lingzhou.agent.backend.capability.agentruntime.RuntimeCapabilityStatus;
import lingzhou.agent.backend.capability.agentruntime.context.RuntimeContextAssembly;
import lingzhou.agent.backend.capability.agentruntime.prompt.RuntimePromptBlock;
import lingzhou.agent.backend.capability.agentruntime.prompt.RuntimePromptPack;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class ObservabilityCapabilityAdapter extends AbstractAgentRuntimeCapability {

    private static final Logger logger = LoggerFactory.getLogger(ObservabilityCapabilityAdapter.class);
    private static final int DEBUG_TEXT_LIMIT = 6000;
    private static final int DEBUG_BLOCK_TEXT_LIMIT = 2000;
    private static final int DEBUG_HISTORY_MESSAGE_LIMIT = 30;

    public ObservabilityCapabilityAdapter() {
        super(RuntimeCapabilitySlot.OBSERVABILITY, "observability", RuntimeCapabilityStatus.ACTIVE);
    }

    public void logRuntimeAssembled(AgentRuntime agentRuntime) {
        if (agentRuntime == null) {
            return;
        }
        logger.debug(
                "Agent runtime assembled: profile={}, capabilities={}",
                agentRuntime.profile(),
                agentRuntime.capabilityNames());
    }

    public void logPromptPack(AgentRuntimeExecutionContext executionContext, RuntimePromptPack promptPack) {
        if (!logger.isDebugEnabled() || executionContext == null || promptPack == null) {
            return;
        }
//        logger.debug(
//                "Agent runtime prompt pack assembled: sessionCode={}, profile={}, pipeline={}, blockCount={}, finalSystemPromptLength={}, blocks={}, finalSystemPrompt=\n{}",
//                sessionCode(executionContext),
//                executionContext.profile(),
//                executionContext.pipeline(),
//                promptPack.systemPromptBlocks().size(),
//                safeLength(promptPack.systemPrompt()),
//                formatPromptBlocks(promptPack.systemPromptBlocks()),
//                truncate(promptPack.systemPrompt(), DEBUG_TEXT_LIMIT));
    }

    public void logContextAssembly(
            AgentRuntimeExecutionContext executionContext, RuntimeContextAssembly contextAssembly) {
        if (!logger.isDebugEnabled() || executionContext == null || contextAssembly == null) {
            return;
        }
                logger.debug(
                        "Agent runtime context assembled: sessionCode={}, profile={}, pipeline={}, historyCount={}, systemPromptLength={}," +
                            " userMessageLength={}, toolCallbacks={}, " +
                            "systemPrompt=\n{}\nuserMessage=\n{}\nhistoryMessages=\n{}",
                        sessionCode(executionContext),
                        executionContext.profile(),
                        executionContext.pipeline(),
                        contextAssembly.historyMessages().size(),
                        safeLength(contextAssembly.systemPrompt()),
                        safeLength(contextAssembly.userMessage()),
                        formatToolCallbacks(contextAssembly.toolCallbacks()),
                       contextAssembly.systemPrompt(),
                       contextAssembly.userMessage(),
                        formatHistoryMessages(contextAssembly.historyMessages()));
    }

    public void logGeneralStreamingChunk(ChatRuntimePreparedRequest prepared, int deltaLength, String contentType) {
        if (prepared == null) {
            return;
        }
        //        logger.debug(
        //                "SSE chat chunk length={}, sessionType={}, scopeId={}, contentType={}",
        //                deltaLength,
        //                prepared.sessionType().name(),
        //                prepared.scopeId(),
        //                contentType);
    }

    public void logStreamingError(
            String scene,
            ChatRuntimePreparedRequest prepared,
            ModelRuntimeConfigResolver.ResolvedChatModelConfig chatConfig,
            Throwable error) {
        if (prepared == null || chatConfig == null) {
            logger.error("聊天流式请求失败：scene={}, error={}", scene, error == null ? "" : error.getMessage(), error);
            return;
        }
        if (error instanceof WebClientResponseException responseException) {
            logger.error(
                    "聊天流式请求失败：scene={}, provider={}, model={}, sessionType={}, scopeId={}, status={}, responseBody={}",
                    scene,
                    chatConfig.provider(),
                    chatConfig.model(),
                    prepared.sessionType().name(),
                    prepared.scopeId(),
                    responseException.getStatusCode().value(),
                    responseException.getResponseBodyAsString(),
                    error);
            return;
        }
        logger.error(
                "聊天流式请求失败：scene={}, provider={}, model={}, sessionType={}, scopeId={}, error={}",
                scene,
                chatConfig.provider(),
                chatConfig.model(),
                prepared.sessionType().name(),
                prepared.scopeId(),
                error == null ? "" : error.getMessage(),
                error);
    }

    public void logSkillContextStats(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || !isSkillLikeSessionType(prepared.sessionType())) {
            return;
        }
        int systemPromptLength = safeLength(prepared.systemPrompt());
        int userMessageLength = safeLength(prepared.userMessage());
        int messageLength = safeLength(prepared.message());
        int totalContextLength = systemPromptLength + userMessageLength;
        logger.debug(
                "Skill runtime context assembled: sessionType={}, scopeId={}, systemPromptLength={}, userMessageLength={}, messageLength={}, totalContextLength={}",
                prepared.sessionType().name(),
                prepared.scopeId(),
                systemPromptLength,
                userMessageLength,
                messageLength,
                totalContextLength);
    }

    public void logExecutionModeContextStats(
            ChatRuntimePreparedRequest prepared, String executionModeHint, boolean usesToolAwarePipeline) {
        if (prepared == null) {
            return;
        }
        logger.debug(
                "[运行时画像] 执行上下文统计：sessionType={}, scopeId={}, executionModeHint={}, usesToolAwarePipeline={}, systemPromptLength={}, userMessageLength={}, toolCallbacks={}",
                prepared.sessionType() == null ? null : prepared.sessionType().name(),
                prepared.scopeId(),
                executionModeHint,
                usesToolAwarePipeline,
                safeLength(prepared.systemPrompt()),
                safeLength(prepared.userMessage()),
                prepared.toolCallbacks() == null ? 0 : prepared.toolCallbacks().size());
    }

    public void logSkillConversationStats(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            String content,
            long startedAt,
            int modelRoundCount) {
        if (prepared == null || !isSkillLikeSessionType(prepared.sessionType())) {
            return;
        }
        long durationMs = Math.max(1L, System.currentTimeMillis() - startedAt);
        int outputLength = safeLength(content);
        double outputCharsPerSecond = outputLength * 1000.0 / durationMs;
        logger.debug(
                "Skill runtime stream completed: sessionCode={}, sessionType={}, scopeId={}, modelRounds={}, outputLength={}, durationMs={}, outputCharsPerSecond={}",
                context == null ? "" : context.sessionCode(),
                prepared.sessionType().name(),
                prepared.scopeId(),
                modelRoundCount,
                outputLength,
                durationMs,
                outputCharsPerSecond);
    }

    public void logModelRoundThroughput(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            int roundIndex,
            int outputLength,
            long roundStartedAt) {
        if (prepared == null || !isSkillLikeSessionType(prepared.sessionType()) || outputLength <= 0) {
            return;
        }
        long durationMs = Math.max(1L, System.currentTimeMillis() - roundStartedAt);
        double outputCharsPerSecond = outputLength * 1000.0 / durationMs;
        logger.debug(
                "Skill runtime model round completed: sessionCode={}, sessionType={}, scopeId={}, roundIndex={}, outputLength={}, durationMs={}, outputCharsPerSecond={}",
                context == null ? "" : context.sessionCode(),
                prepared.sessionType().name(),
                prepared.scopeId(),
                roundIndex,
                outputLength,
                durationMs,
                outputCharsPerSecond);
    }

    private boolean isSkillLikeSessionType(ConversationSessionType sessionType) {
        return sessionType == ConversationSessionType.SKILL_CHAT
                || sessionType == ConversationSessionType.PUBLISHED_SKILL_CHAT
                || sessionType == ConversationSessionType.SKILL_STUDIO_PROJECT_PREVIEW_CHAT;
    }

    private String sessionCode(AgentRuntimeExecutionContext executionContext) {
        return executionContext.conversation() == null
                ? ""
                : executionContext.conversation().sessionCode();
    }

    private String formatPromptBlocks(List<RuntimePromptBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "[]";
        }
        List<String> values = new ArrayList<>();
        for (RuntimePromptBlock block : blocks) {
            if (block == null || !block.hasContent()) {
                continue;
            }
            values.add("{type=%s, source=%s, order=%s, length=%s, content=\"%s\"}"
                    .formatted(
                            block.sourceType(),
                            block.source(),
                            block.order(),
                            safeLength(block.content()),
                            oneLine(truncate(block.content(), DEBUG_BLOCK_TEXT_LIMIT))));
        }
        return values.toString();
    }

    private String formatHistoryMessages(List<Message> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return "[]";
        }
        List<String> values = new ArrayList<>();
        int count = Math.min(historyMessages.size(), DEBUG_HISTORY_MESSAGE_LIMIT);
        for (int i = 0; i < count; i++) {
            Message message = historyMessages.get(i);
            if (message == null) {
                continue;
            }
            String text = message.getText();
            values.add("{index=%s, type=%s, length=%s, text=\"%s\"}"
                    .formatted(
                            i,
                            message.getMessageType(),
                            safeLength(text),
                            oneLine(truncate(text, DEBUG_BLOCK_TEXT_LIMIT))));
        }
        if (historyMessages.size() > DEBUG_HISTORY_MESSAGE_LIMIT) {
            values.add(
                    "{truncated=true, remaining=%s}".formatted(historyMessages.size() - DEBUG_HISTORY_MESSAGE_LIMIT));
        }
        return values.toString();
    }

    private String formatToolCallbacks(List<ToolCallback> toolCallbacks) {
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            return "[]";
        }
        List<String> names = new ArrayList<>();
        for (ToolCallback callback : toolCallbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            names.add(callback.getToolDefinition().name());
        }
        return names.toString();
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        if (limit <= 0 || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...<truncated " + (value.length() - limit) + " chars>";
    }

    private String oneLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
