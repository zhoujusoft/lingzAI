package lingzhou.agent.backend.capability.agentruntime.execution;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.RuntimeExecutionFacade;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionMode;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolContext;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolInvocationContextHolder;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Component
public class RuntimeToolExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeToolExecutionService.class);

    private final RuntimeExecutionProperties runtimeExecutionProperties;
    private final RuntimeExecutionFacade runtimeExecutionFacade;
    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;

    public RuntimeToolExecutionService(
            RuntimeExecutionProperties runtimeExecutionProperties,
            RuntimeExecutionFacade runtimeExecutionFacade,
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService) {
        this.runtimeExecutionProperties = runtimeExecutionProperties;
        this.runtimeExecutionFacade = runtimeExecutionFacade;
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
    }

    public void prepareWorkspaceIfNeeded(
            ChatRuntimePreparedRequest prepared,
            ConversationHistoryService.ConversationContext context,
            SkillKit requestSkillKit) {
        if (prepared == null || context == null || !hasMaterializedFiles(prepared.fileListJson())) {
            return;
        }
        RuntimeToolContext runtimeToolContext = buildRuntimeToolContext(prepared, context, requestSkillKit);
        try {
            runtimeExecutionFacade.prepareWorkspace(runtimeToolContext);
        } catch (Exception ex) {
            logger.error(
                    "准备带附件会话工作区失败：sessionId={}, userId={}, error={}",
                    context.sessionCode(),
                    context.createUserId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    public ChatRuntimePreparedRequest bindRuntimeToolCallbacks(
            ChatRuntimePreparedRequest prepared,
            ConversationHistoryService.ConversationContext context,
            SkillKit requestSkillKit) {
        if (prepared == null
                || context == null
                || prepared.toolCallbacks() == null
                || prepared.toolCallbacks().isEmpty()) {
            return prepared;
        }
        RuntimeToolContext runtimeToolContext = buildRuntimeToolContext(prepared, context, requestSkillKit);
        return new ChatRuntimePreparedRequest(
                prepared.sessionType(),
                prepared.scopeType(),
                prepared.sessionId(),
                prepared.scopeId(),
                prepared.scopeDisplayName(),
                prepared.message(),
                prepared.userMessage(),
                prepared.messageType(),
                prepared.questionType(),
                prepared.paramsJson(),
                prepared.fileListJson(),
                prepared.toolCallbacks().stream()
                        .map(callback -> wrapToolCallback(callback, runtimeToolContext))
                        .toList(),
                prepared.systemPrompt(),
                prepared.systemPromptAppend(),
                prepared.runtimeSkillName(),
                prepared.availableSkills(),
                prepared.loadedSkills(),
                prepared.chatModelId(),
                prepared.personalAgent(),
                prepared.personalAgentMode());
    }

    public List<ToolCallback> bindToolCallbacks(
            List<ToolCallback> callbacks,
            ChatRuntimePreparedRequest prepared,
            ConversationHistoryService.ConversationContext context,
            SkillKit requestSkillKit) {
        if (callbacks == null || callbacks.isEmpty() || prepared == null || context == null) {
            return callbacks == null ? List.of() : List.copyOf(callbacks);
        }
        RuntimeToolContext runtimeToolContext = buildRuntimeToolContext(prepared, context, requestSkillKit);
        return callbacks.stream()
                .map(callback -> wrapToolCallback(callback, runtimeToolContext))
                .toList();
    }

    public Flux<ServerSentEvent<String>> withRuntimeToolContext(
            Flux<ServerSentEvent<String>> stream,
            ChatRuntimePreparedRequest prepared,
            ConversationHistoryService.ConversationContext context,
            SkillKit requestSkillKit) {
        if (stream == null || prepared == null || context == null) {
            return stream;
        }
        RuntimeToolContext runtimeToolContext = buildRuntimeToolContext(prepared, context, requestSkillKit);
        return Flux.defer(() -> {
            RuntimeToolInvocationContextHolder.set(runtimeToolContext);
            return stream.doFinally(signalType -> RuntimeToolInvocationContextHolder.clear());
        });
    }

    private RuntimeToolContext buildRuntimeToolContext(
            ChatRuntimePreparedRequest prepared,
            ConversationHistoryService.ConversationContext context,
            SkillKit requestSkillKit) {
        RuntimeToolContext runtimeToolContext = new RuntimeToolContext(
                context.sessionCode(),
                context.createUserId(),
                extractRunId(prepared.paramsJson()),
                prepared.scopeType(),
                prepared.scopeId(),
                prepared.runtimeSkillName(),
                () -> requestScopedSkillRuntimeService.resolveCurrentRuntimeSkillName(requestSkillKit, prepared),
                runtimeExecutionProperties.getMode() == null
                        ? RuntimeExecutionMode.NATIVE
                        : runtimeExecutionProperties.getMode(),
                prepared.fileListJson(),
                prepared.paramsJson(),
                prepared.personalAgent(),
                prepared.personalAgentMode(),
                context.userMessageId(),
                context.assistantMessageId());
        logger.debug(
                "[运行时上下文] 已构建：会话编码={}, 个人Agent={}, 个人模式={}, 当前技能={}, 作用域类型={}, 作用域ID={}",
                context.sessionCode(),
                prepared.personalAgent(),
                prepared.personalAgentMode(),
                runtimeToolContext.currentRuntimeSkillName(),
                prepared.scopeType(),
                prepared.scopeId());
        return runtimeToolContext;
    }

    private Long extractRunId(String paramsJson) {
        if (!StringUtils.hasText(paramsJson)) {
            return null;
        }
        try {
            Map<String, Object> payload = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            Object value = payload == null ? null : payload.get("runId");
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                long longValue = number.longValue();
                return longValue > 0 ? longValue : null;
            }
            String text = String.valueOf(value).trim();
            if (!StringUtils.hasText(text)) {
                return null;
            }
            long longValue = Long.parseLong(text);
            return longValue > 0 ? longValue : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean hasMaterializedFiles(String fileListJson) {
        if (!StringUtils.hasText(fileListJson)) {
            return false;
        }
        String normalized = fileListJson.trim();
        return !normalized.isEmpty() && !"[]".equals(normalized);
    }

    private ToolCallback wrapToolCallback(ToolCallback delegate, RuntimeToolContext runtimeToolContext) {
        if (delegate == null) {
            return null;
        }
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
                RuntimeToolInvocationContextHolder.set(runtimeToolContext);
                try {
                    return delegate.call(toolInput);
                } finally {
                    RuntimeToolInvocationContextHolder.clear();
                }
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                RuntimeToolInvocationContextHolder.set(runtimeToolContext);
                try {
                    return delegate.call(toolInput, toolContext);
                } finally {
                    RuntimeToolInvocationContextHolder.clear();
                }
            }
        };
    }
}
