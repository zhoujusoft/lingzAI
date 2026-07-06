package lingzhou.agent.backend.business.chat.service;

import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequestResolver;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.license.service.LicenseService;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntime;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeExecutionContext;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeFactory;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeOrchestrator;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeProfileResolution;
import lingzhou.agent.backend.capability.agentruntime.AgentRuntimeProfileResolver;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeErrorMessageResolver;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatRuntimeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ChatRuntimeExecutor.class);

    private final ConversationHistoryService conversationHistoryService;
    private final ChatRuntimePreparedRequestResolver preparedRequestResolver;
    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;
    private final AgentRuntimeFactory agentRuntimeFactory;
    private final AgentRuntimeProfileResolver agentRuntimeProfileResolver;
    private final AgentRuntimeOrchestrator agentRuntimeOrchestrator;
    private final LicenseService licenseService;
    private final UserTokenQuotaService userTokenQuotaService;

    public ChatRuntimeExecutor(
            ConversationHistoryService conversationHistoryService,
            ChatRuntimePreparedRequestResolver preparedRequestResolver,
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService,
            AgentRuntimeFactory agentRuntimeFactory,
            AgentRuntimeProfileResolver agentRuntimeProfileResolver,
            AgentRuntimeOrchestrator agentRuntimeOrchestrator,
            LicenseService licenseService,
            UserTokenQuotaService userTokenQuotaService) {
        this.conversationHistoryService = conversationHistoryService;
        this.preparedRequestResolver = preparedRequestResolver;
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
        this.agentRuntimeFactory = agentRuntimeFactory;
        this.agentRuntimeProfileResolver = agentRuntimeProfileResolver;
        this.agentRuntimeOrchestrator = agentRuntimeOrchestrator;
        this.licenseService = licenseService;
        this.userTokenQuotaService = userTokenQuotaService;
    }

    public Flux<ServerSentEvent<String>> stream(ChatRuntimePreparedRequest prepared, Long userId) {
        ChatRuntimePreparedRequest preparedWithExecutionSnapshot = preparedRequestResolver.resolve(prepared, userId);
        String licenseError =
                licenseService.validateConversationAccess(userId, preparedWithExecutionSnapshot.sessionType());
        if (org.springframework.util.StringUtils.hasText(licenseError)) {
            return persistTerminalFailureResponse(preparedWithExecutionSnapshot, userId, licenseError);
        }
        String quotaError = userTokenQuotaService.validateQuota(userId, preparedWithExecutionSnapshot.sessionType());
        if (org.springframework.util.StringUtils.hasText(quotaError)) {
            return persistTerminalFailureResponse(preparedWithExecutionSnapshot, userId, quotaError);
        }
        ConversationHistoryService.ConversationContext context;
        try {
            context = conversationHistoryService.startMessage(
                    userId,
                    preparedWithExecutionSnapshot.sessionType(),
                    preparedWithExecutionSnapshot.sessionId(),
                    preparedWithExecutionSnapshot.scopeId(),
                    preparedWithExecutionSnapshot.scopeDisplayName(),
                    preparedWithExecutionSnapshot.message(),
                    preparedWithExecutionSnapshot.messageType(),
                    preparedWithExecutionSnapshot.message(),
                    preparedWithExecutionSnapshot.questionType(),
                    preparedWithExecutionSnapshot.paramsJson(),
                    preparedWithExecutionSnapshot.fileListJson(),
                    preparedWithExecutionSnapshot.chatModelId());
        } catch (Exception ex) {
            logger.error("初始化会话消息失败：error={}", ex.getMessage(), ex);
            String message = hasText(ex.getMessage()) ? ex.getMessage() : "会话初始化失败，请稍后重试";
            return Flux.just(errorEvent(message));
        }

        try {
            AgentRuntimeProfileResolution profileResolution =
                    agentRuntimeProfileResolver.resolve(preparedWithExecutionSnapshot);
            AgentRuntime agentRuntime = agentRuntimeFactory.create(profileResolution.profile());
            SkillKit requestSkillKit = profileResolution.usesToolAwarePipeline()
                    ? requestScopedSkillRuntimeService.buildSkillKit(preparedWithExecutionSnapshot)
                    : null;
            logger.debug(
                    "[运行时画像] 执行上下文已创建：sessionId={}, usesToolAwarePipeline={}, requestSkillKitBuilt={}",
                    preparedWithExecutionSnapshot.sessionId(),
                    profileResolution.usesToolAwarePipeline(),
                    requestSkillKit != null);
            AgentRuntimeExecutionContext executionContext = new AgentRuntimeExecutionContext(
                    preparedWithExecutionSnapshot,
                    userId,
                    context,
                    profileResolution,
                    agentRuntime,
                    requestSkillKit,
                    requestScopedSkillRuntimeService);
            return agentRuntimeOrchestrator.execute(executionContext);
        } catch (Exception ex) {
            String friendlyMessage = ModelRuntimeErrorMessageResolver.resolve(ex);
            logger.error("运行时编排失败：sessionId={}, error={}", context.sessionId(), ex.getMessage(), ex);
            persistStartedMessageFailure(context, preparedWithExecutionSnapshot, friendlyMessage);
            return Flux.just(messageEvent(friendlyMessage), doneEvent());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ServerSentEvent<String> errorEvent(String error) {
        return ChatSseEventBuilder.error(error);
    }

    private Flux<ServerSentEvent<String>> persistTerminalFailureResponse(
            ChatRuntimePreparedRequest prepared, Long userId, String errorMessage) {
        if (prepared == null || userId == null || userId <= 0) {
            return Flux.just(errorEvent(errorMessage));
        }
        try {
            ConversationHistoryService.ConversationContext context = conversationHistoryService.startMessage(
                    userId,
                    prepared.sessionType(),
                    prepared.sessionId(),
                    prepared.scopeId(),
                    prepared.scopeDisplayName(),
                    prepared.message(),
                    prepared.messageType(),
                    prepared.message(),
                    prepared.questionType(),
                    prepared.paramsJson(),
                    prepared.fileListJson(),
                    prepared.chatModelId());
            persistStartedMessageFailure(context, prepared, errorMessage);
            return Flux.just(messageEvent(errorMessage), doneEvent());
        } catch (Exception ex) {
            logger.error("终态失败回复持久化失败：error={}", ex.getMessage(), ex);
            return Flux.just(errorEvent(errorMessage));
        }
    }

    public Flux<ServerSentEvent<String>> persistPreRuntimeFailure(
            ChatRuntimePreparedRequest prepared, Long userId, String errorMessage) {
        return persistTerminalFailureResponse(prepared, userId, errorMessage);
    }

    private void persistStartedMessageFailure(
            ConversationHistoryService.ConversationContext context,
            ChatRuntimePreparedRequest prepared,
            String errorMessage) {
        if (context == null) {
            return;
        }
        conversationHistoryService.failMessage(context, errorMessage, errorMessage, prepared.paramsJson(), 0L, null);
    }

    private ServerSentEvent<String> messageEvent(String message) {
        return ChatSseEventBuilder.message(message);
    }

    private ServerSentEvent<String> doneEvent() {
        return ChatSseEventBuilder.done();
    }
}
