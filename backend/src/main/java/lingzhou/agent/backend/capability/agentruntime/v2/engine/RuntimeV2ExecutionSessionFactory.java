package lingzhou.agent.backend.capability.agentruntime.v2.engine;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.business.chat.service.ConversationRunConstants;
import lingzhou.agent.backend.business.chat.service.ConversationRunContext;
import lingzhou.agent.backend.business.chat.service.ConversationRunService;
import lingzhou.agent.backend.capability.agentruntime.capabilities.RuntimeExecutionCapabilityAdapter;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2ExecutionSessionFactory {

    private final ConversationHistoryService conversationHistoryService;
    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;
    private final RuntimeExecutionCapabilityAdapter runtimeExecutionCapability;
    private final ConversationRunService conversationRunService;
    private final RuntimeV2PreparedRequestResolver preparedRequestResolver;

    public RuntimeV2ExecutionSessionFactory(
            ConversationHistoryService conversationHistoryService,
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService,
            RuntimeExecutionCapabilityAdapter runtimeExecutionCapability,
            ConversationRunService conversationRunService,
            RuntimeV2PreparedRequestResolver preparedRequestResolver) {
        this.conversationHistoryService = conversationHistoryService;
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
        this.runtimeExecutionCapability = runtimeExecutionCapability;
        this.conversationRunService = conversationRunService;
        this.preparedRequestResolver = preparedRequestResolver;
    }

    public ChatRuntimePreparedRequest resolvePreparedRequest(ChatRuntimePreparedRequest prepared, Long userId) {
        return preparedRequestResolver.resolve(prepared, userId);
    }

    public RuntimeV2ExecutionSession create(ChatRuntimePreparedRequest prepared, Long userId) {
        ChatRuntimePreparedRequest resolvedPrepared = resolvePreparedRequest(prepared, userId);
        return createResolved(resolvedPrepared, userId);
    }

    public RuntimeV2ExecutionSession createResolved(ChatRuntimePreparedRequest resolvedPrepared, Long userId) {
        ConversationHistoryService.ConversationContext context = conversationHistoryService.startMessage(
                userId,
                resolvedPrepared.sessionType(),
                resolvedPrepared.sessionId(),
                resolvedPrepared.scopeId(),
                resolvedPrepared.scopeDisplayName(),
                resolvedPrepared.message(),
                resolvedPrepared.messageType(),
                resolvedPrepared.message(),
                resolvedPrepared.questionType(),
                resolvedPrepared.paramsJson(),
                resolvedPrepared.fileListJson(),
                resolvedPrepared.chatModelId());
        ChatRuntimePreparedRequest preparedWithSession = mergeSessionContext(resolvedPrepared, context);
        ConversationRunContext runContext = startConversationRun(context, userId, preparedWithSession);
        ChatRuntimePreparedRequest preparedWithRun = mergeRunContext(preparedWithSession, runContext);
        SkillKit requestSkillKit = requestScopedSkillRuntimeService.buildSkillKit(preparedWithRun);
        runtimeExecutionCapability.prepareWorkspaceIfNeeded(preparedWithRun, context, requestSkillKit);
        ChatRuntimePreparedRequest preparedWithTools =
                runtimeExecutionCapability.bindRuntimeToolCallbacks(preparedWithRun, context, requestSkillKit);
        return new RuntimeV2ExecutionSession(
                preparedWithRun,
                preparedWithTools,
                context,
                requestSkillKit,
                buildExecutionReadyToolIndex(preparedWithTools, requestSkillKit),
                runContext);
    }

    private ChatRuntimePreparedRequest mergeSessionContext(
            ChatRuntimePreparedRequest prepared, ConversationHistoryService.ConversationContext context) {
        if (prepared == null || context == null || !StringUtils.hasText(context.sessionCode())) {
            return prepared;
        }
        String normalizedSessionCode = context.sessionCode().trim();
        if (normalizedSessionCode.equals(StringUtils.trimWhitespace(prepared.sessionId()))) {
            return prepared;
        }
        return prepared.withSessionId(normalizedSessionCode);
    }

    private ConversationRunContext startConversationRun(
            ConversationHistoryService.ConversationContext context, Long userId, ChatRuntimePreparedRequest prepared) {
        if (context == null || context.sessionId() == null || context.assistantMessageId() == null) {
            return new ConversationRunContext(null, "", ConversationRunConstants.RUN_TYPE_CHAT);
        }
        var run = conversationRunService.startRun(
                context.sessionId(),
                context.userMessageId(),
                userId,
                ConversationRunConstants.RUN_TYPE_CHAT,
                ConversationRunConstants.STATUS_PENDING,
                ConversationRunConstants.PHASE_TRIAGE,
                null,
                null,
                prepared == null ? null : prepared.runtimeSkillName(),
                null);
        return new ConversationRunContext(
                run == null ? null : run.getId(),
                run == null ? "" : run.getRunCode(),
                run == null ? ConversationRunConstants.RUN_TYPE_CHAT : run.getRunType());
    }

    private ChatRuntimePreparedRequest mergeRunContext(
            ChatRuntimePreparedRequest prepared, ConversationRunContext runContext) {
        if (prepared == null || runContext == null || runContext.runId() == null || runContext.runId() <= 0) {
            return prepared;
        }
        Map<String, Object> payload = parseParamsJson(prepared.paramsJson());
        payload.put("runId", runContext.runId());
        payload.put("runCode", runContext.runCode());
        payload.put("runType", runContext.runType());
        return prepared.withParamsJson(JSON.toJSONString(payload));
    }

    private Map<String, Object> parseParamsJson(String paramsJson) {
        if (!StringUtils.hasText(paramsJson)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> payload = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            return payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, ToolCallback> buildExecutionReadyToolIndex(
            ChatRuntimePreparedRequest prepared, SkillKit requestSkillKit) {
        Map<String, ToolCallback> index = new LinkedHashMap<>();
        if (prepared != null && prepared.toolCallbacks() != null) {
            for (ToolCallback callback : prepared.toolCallbacks()) {
                registerExecutionReadyTool(index, prepared, requestSkillKit, callback);
            }
        }
        if (requestSkillKit != null) {
            for (ToolCallback callback : requestSkillKit.getSkillLoaderTools()) {
                registerExecutionReadyTool(index, prepared, requestSkillKit, callback);
            }
            for (ToolCallback callback : requestSkillKit.getAllActiveTools()) {
                registerExecutionReadyTool(index, prepared, requestSkillKit, callback);
            }
        }
        return Map.copyOf(index);
    }

    private void registerExecutionReadyTool(
            Map<String, ToolCallback> index,
            ChatRuntimePreparedRequest prepared,
            SkillKit requestSkillKit,
            ToolCallback callback) {
        if (index == null || callback == null || callback.getToolDefinition() == null) {
            return;
        }
        String toolName = callback.getToolDefinition().name();
        if (!StringUtils.hasText(toolName)) {
            return;
        }
        ToolCallback executionReady = wrapWithSkillExecutionScope(prepared, requestSkillKit, callback);
        index.putIfAbsent(toolName.trim(), executionReady);
    }

    private ToolCallback wrapWithSkillExecutionScope(
            ChatRuntimePreparedRequest prepared, SkillKit requestSkillKit, ToolCallback delegate) {
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                return runtimeExecutionCapability.callWithSkillExecutionScope(
                        prepared, requestSkillKit, () -> delegate.call(toolInput));
            }

            @Override
            public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
                return runtimeExecutionCapability.callWithSkillExecutionScope(
                        prepared, requestSkillKit, () -> delegate.call(toolInput, toolContext));
            }
        };
    }

    public record RuntimeV2ExecutionSession(
            ChatRuntimePreparedRequest resolvedPrepared,
            ChatRuntimePreparedRequest preparedWithTools,
            ConversationHistoryService.ConversationContext context,
            SkillKit requestSkillKit,
            Map<String, ToolCallback> executionReadyToolIndex,
            ConversationRunContext runContext) {}
}
