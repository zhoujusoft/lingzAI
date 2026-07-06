package lingzhou.agent.backend.capability.agentruntime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.Map;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.service.ChatSseEventBuilder;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.capability.agentruntime.capabilities.ObservabilityCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.capabilities.RuntimeExecutionCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.capabilities.ToolCallingCapabilityAdapter;
import lingzhou.agent.backend.capability.agentruntime.context.ContextEngineeringService;
import lingzhou.agent.backend.capability.agentruntime.context.RuntimeContextAssembly;
import lingzhou.agent.backend.capability.agentruntime.model.RuntimeModelRequest;
import lingzhou.agent.backend.capability.agentruntime.personal.PersonalAgentPreflightService;
import lingzhou.agent.backend.capability.agentruntime.pipeline.AgentRuntimeStreamingPipeline;
import lingzhou.agent.backend.capability.agentruntime.prompt.PromptEngineeringService;
import lingzhou.agent.backend.capability.agentruntime.prompt.RuntimePromptPack;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class AgentRuntimeOrchestrator {

    private final ConversationHistoryService conversationHistoryService;
    private final ModelRuntimeClientFactory modelRuntimeClientFactory;
    private final ContextEngineeringService contextEngineeringService;
    private final ObservabilityCapabilityAdapter observabilityCapability;
    private final PromptEngineeringService promptEngineeringService;
    private final RuntimeExecutionCapabilityAdapter runtimeExecutionCapability;
    private final ToolCallingCapabilityAdapter toolCallingCapability;
    private final AgentRuntimeStreamingPipeline streamingPipeline;
    private final PersonalAgentPreflightService personalAgentPreflightService;

    public AgentRuntimeOrchestrator(
            ConversationHistoryService conversationHistoryService,
            ModelRuntimeClientFactory modelRuntimeClientFactory,
            ContextEngineeringService contextEngineeringService,
            ObservabilityCapabilityAdapter observabilityCapability,
            PromptEngineeringService promptEngineeringService,
            RuntimeExecutionCapabilityAdapter runtimeExecutionCapability,
            ToolCallingCapabilityAdapter toolCallingCapability,
            AgentRuntimeStreamingPipeline streamingPipeline,
            PersonalAgentPreflightService personalAgentPreflightService) {
        this.conversationHistoryService = conversationHistoryService;
        this.modelRuntimeClientFactory = modelRuntimeClientFactory;
        this.contextEngineeringService = contextEngineeringService;
        this.observabilityCapability = observabilityCapability;
        this.promptEngineeringService = promptEngineeringService;
        this.runtimeExecutionCapability = runtimeExecutionCapability;
        this.toolCallingCapability = toolCallingCapability;
        this.streamingPipeline = streamingPipeline;
        this.personalAgentPreflightService = personalAgentPreflightService;
    }

    public Flux<ServerSentEvent<String>> execute(AgentRuntimeExecutionContext executionContext) {
        ChatRuntimePreparedRequest prepared = executionContext.prepared();
        ConversationHistoryService.ConversationContext conversation = executionContext.conversation();
        log.debug(
                "[运行时编排] 开始执行：会话编码={}, 个人Agent={}, 个人模式={}, 使用工具感知管线={}, 启用运行时执行={}, 启用工具调用={}",
                conversation == null ? null : conversation.sessionCode(),
                prepared != null && prepared.personalAgent(),
                prepared == null ? null : prepared.personalAgentMode(),
                executionContext != null && executionContext.usesToolAwarePipeline(),
                hasActiveCapability(executionContext, RuntimeCapabilitySlot.RUNTIME_EXECUTION),
                hasActiveCapability(executionContext, RuntimeCapabilitySlot.TOOL_CALLING));

        if (hasActiveCapability(executionContext, RuntimeCapabilitySlot.OBSERVABILITY)) {
            observabilityCapability.logRuntimeAssembled(executionContext.agentRuntime());
        }
        if (hasActiveCapability(executionContext, RuntimeCapabilitySlot.RUNTIME_EXECUTION)) {
            runtimeExecutionCapability.prepareWorkspaceIfNeeded(
                    prepared, conversation, executionContext.requestSkillKit());
            prepared = runtimeExecutionCapability.bindRuntimeToolCallbacks(
                    prepared, conversation, executionContext.requestSkillKit());
            log.debug(
                    "[运行时编排] 已绑定运行时工具上下文：会话编码={}, 工具数={}",
                    conversation == null ? null : conversation.sessionCode(),
                    prepared == null || prepared.toolCallbacks() == null
                            ? 0
                            : prepared.toolCallbacks().size());
        }

        AgentRuntimeExecutionContext preparedExecutionContext = executionContext.withPrepared(prepared);
        PersonalAgentPreflightService.PreflightResult preflightResult =
                personalAgentPreflightService.prepare(conversation, null, prepared);
        log.debug(
                "[运行时编排] 预检结果：会话编码={}, 状态={}, 是否终止={}",
                conversation == null ? null : conversation.sessionCode(),
                preflightResult.status(),
                preflightResult.terminal());
        if (preflightResult.terminal()) {
            log.debug(
                    "[运行时编排] 进入预检终止分支：会话编码={}, 状态={}, 响应={}",
                    conversation == null ? null : conversation.sessionCode(),
                    preflightResult.status(),
                    preflightResult.responseMessage());
            Flux<ServerSentEvent<String>> meta =
                    Flux.just(metaEvent(conversationHistoryService.buildMetaPayload(conversation)));
            return meta.concatWith(Flux.just(
                    ChatSseEventBuilder.message(preflightResult.responseMessage()), ChatSseEventBuilder.done()));
        }
        log.debug("[运行时编排] 预检通过，开始构建模型请求：会话编码={}", conversation == null ? null : conversation.sessionCode());
        RuntimeModelRequest modelRequest = prepareBaseModelRequest(preparedExecutionContext);
        if (hasActiveCapability(preparedExecutionContext, RuntimeCapabilitySlot.OBSERVABILITY)) {
            observabilityCapability.logSkillContextStats(prepared);
            observabilityCapability.logExecutionModeContextStats(
                    prepared, resolveExecutionModeHint(prepared), preparedExecutionContext.usesToolAwarePipeline());
        }
        Flux<ServerSentEvent<String>> meta =
                Flux.just(metaEvent(conversationHistoryService.buildMetaPayload(conversation)));
        Flux<ServerSentEvent<String>> stream = streamingPipeline.execute(preparedExecutionContext, modelRequest);
        if (preparedExecutionContext.usesToolAwarePipeline()
                && hasActiveCapability(preparedExecutionContext, RuntimeCapabilitySlot.RUNTIME_EXECUTION)) {
            stream = runtimeExecutionCapability.withSkillExecutionScope(
                    stream, prepared, preparedExecutionContext.requestSkillKit());
        }
        if (hasActiveCapability(preparedExecutionContext, RuntimeCapabilitySlot.RUNTIME_EXECUTION)) {
            stream = runtimeExecutionCapability.withRuntimeToolContext(
                    stream, prepared, conversation, preparedExecutionContext.requestSkillKit());
        }
        return meta.concatWith(stream);
    }

    private RuntimeModelRequest prepareBaseModelRequest(AgentRuntimeExecutionContext executionContext) {
        ModelRuntimeClientFactory.ChatRuntimeBundle chatRuntimeBundle = shouldUseToolCallingBundle(executionContext)
                ? toolCallingCapability.createSkillChatBundle(executionContext)
                : modelRuntimeClientFactory.createChatBundleWithoutDefaultSystem(
                        executionContext == null || executionContext.prepared() == null
                                ? null
                                : executionContext.prepared().chatModelId());
        RuntimePromptPack promptPack =
                promptEngineeringService.resolvePromptPack(executionContext, chatRuntimeBundle.config());
        log.debug(
                "[运行时编排] 提示词包已构建：会话编码={}, 提示词块数={}, 使用工具调用Bundle={}",
                executionContext == null || executionContext.conversation() == null
                        ? null
                        : executionContext.conversation().sessionCode(),
                promptPack == null || promptPack.systemPromptBlocks() == null
                        ? 0
                        : promptPack.systemPromptBlocks().size(),
                shouldUseToolCallingBundle(executionContext));
        RuntimeContextAssembly contextAssembly = contextEngineeringService.assembleContext(
                executionContext.conversation(), executionContext.prepared(), promptPack);
        if (hasActiveCapability(executionContext, RuntimeCapabilitySlot.OBSERVABILITY)) {
            observabilityCapability.logPromptPack(executionContext, promptPack);
            observabilityCapability.logContextAssembly(executionContext, contextAssembly);
        }
        return contextEngineeringService.buildModelRequest(chatRuntimeBundle, contextAssembly);
    }

    private boolean shouldUseToolCallingBundle(AgentRuntimeExecutionContext executionContext) {
        return executionContext.usesToolAwarePipeline()
                && hasActiveCapability(executionContext, RuntimeCapabilitySlot.TOOL_CALLING);
    }

    private boolean hasActiveCapability(AgentRuntimeExecutionContext executionContext, RuntimeCapabilitySlot slot) {
        return executionContext != null && executionContext.hasActiveCapability(slot);
    }

    private ServerSentEvent<String> metaEvent(Map<String, Object> content) {
        return ChatSseEventBuilder.meta(content);
    }

    private String resolveExecutionModeHint(ChatRuntimePreparedRequest prepared) {
        if (prepared == null
                || prepared.paramsJson() == null
                || prepared.paramsJson().isBlank()) {
            return "";
        }
        try {
            Map<String, Object> payload =
                    JSON.parseObject(prepared.paramsJson(), new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                return "";
            }
            Object value = payload.get("executionModeHint");
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
