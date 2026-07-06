package lingzhou.agent.backend.business.chatv2.service;

import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.ConversationRun;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequestAssembler;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimeRequestMapper;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.business.chat.service.ChatConversationService;
import lingzhou.agent.backend.business.chat.service.ChatRuntimeExecutor;
import lingzhou.agent.backend.business.chat.service.ConversationRunConstants;
import lingzhou.agent.backend.business.chat.service.ConversationRunService;
import lingzhou.agent.backend.capability.agentruntime.approval.RuntimeApprovalService;
import lingzhou.agent.backend.capability.agentruntime.v2.engine.RuntimeV2ExecutionGateway;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
public class ChatConversationV2Service {

    private final ChatRuntimePreparedRequestAssembler chatRuntimePreparedRequestAssembler;
    private final ChatRuntimeExecutor chatRuntimeExecutor;
    private final RuntimeV2ExecutionGateway runtimeV2ExecutionGateway;
    private final ConversationRunService conversationRunService;
    private final RuntimeApprovalService runtimeApprovalService;

    public ChatConversationV2Service(
            ChatRuntimePreparedRequestAssembler chatRuntimePreparedRequestAssembler,
            ChatRuntimeExecutor chatRuntimeExecutor,
            RuntimeV2ExecutionGateway runtimeV2ExecutionGateway,
            ConversationRunService conversationRunService,
            RuntimeApprovalService runtimeApprovalService) {
        this.chatRuntimePreparedRequestAssembler = chatRuntimePreparedRequestAssembler;
        this.chatRuntimeExecutor = chatRuntimeExecutor;
        this.runtimeV2ExecutionGateway = runtimeV2ExecutionGateway;
        this.conversationRunService = conversationRunService;
        this.runtimeApprovalService = runtimeApprovalService;
    }

    public Flux<ServerSentEvent<String>> streamGeneral(
            ChatConversationService.GeneralChatRequest request, Long userId) {
        LingzRuntimeRequest normalized = ChatRuntimeRequestMapper.forGeneral(request);
        if (!chatRuntimePreparedRequestAssembler.hasRequestContent(normalized.message(), normalized.fileIds())) {
            return persistFrontFailure(
                    buildFallbackPrepared(ConversationSessionType.GENERAL_CHAT_V2, normalized),
                    userId,
                    "message or file is required");
        }
        ChatRuntimePreparedRequest prepared = chatRuntimePreparedRequestAssembler.buildGeneral(
                ConversationSessionType.GENERAL_CHAT_V2, normalized, userId);
        return runtimeV2ExecutionGateway.stream(prepared, userId);
    }

    public CancelRunResponse cancelRun(String runCode, Long userId, CancelRunRequest request) throws TaskException {
        String normalizedRunCode = normalizeRequired(runCode, "runCode 不能为空");
        ConversationRun run = conversationRunService.findByRunCode(normalizedRunCode);
        if (run == null) {
            throw new TaskException("执行记录不存在", TaskException.Code.UNKNOWN);
        }
        if (userId == null || !userId.equals(run.getCreateUserId())) {
            throw new TaskException("无权限终止该执行", TaskException.Code.UNKNOWN);
        }
        if (!ConversationRunConstants.STATUS_RUNNING.equalsIgnoreCase(normalizeText(run.getStatus()))
                && !ConversationRunConstants.STATUS_PENDING.equalsIgnoreCase(normalizeText(run.getStatus()))) {
            return new CancelRunResponse(false, run.getRunCode(), run.getStatus(), "执行已结束");
        }
        String reason = normalizeText(request == null ? "" : request.reason());
        if (!StringUtils.hasText(reason)) {
            reason = "已终止本次执行。";
        }
        boolean accepted = runtimeV2ExecutionGateway.requestCancellation(run.getRunCode(), userId, reason);
        return new CancelRunResponse(accepted, run.getRunCode(), run.getStatus(), accepted ? "终止请求已发送" : "执行不在运行中");
    }

    public RuntimeApprovalService.ApprovalDecisionResponse approveRun(
            String runCode, String approvalCode, Long userId, ApprovalDecisionRequest request) throws TaskException {
        return runtimeApprovalService.approve(runCode, approvalCode, userId, request == null ? "" : request.comment());
    }

    public RuntimeApprovalService.ApprovalDecisionResponse rejectRun(
            String runCode, String approvalCode, Long userId, ApprovalDecisionRequest request) throws TaskException {
        return runtimeApprovalService.reject(runCode, approvalCode, userId, request == null ? "" : request.comment());
    }

    public Long resolveUserId(HttpServletRequest request) {
        Object value = request.getAttribute("UserId");
        if (value == null) {
            throw new IllegalStateException("UserId missing");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Flux<ServerSentEvent<String>> persistFrontFailure(
            ChatRuntimePreparedRequest prepared, Long userId, String errorMessage) {
        return chatRuntimeExecutor.persistPreRuntimeFailure(prepared, userId, errorMessage);
    }

    private ChatRuntimePreparedRequest buildFallbackPrepared(
            ConversationSessionType sessionType, LingzRuntimeRequest normalized) {
        String resolvedMessage = resolveFallbackMessage(normalized);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "v2-fallback-error");
        params.put("runtimeVersion", "v2");
        params.put(
                "scopeType",
                normalized == null || normalized.scopeType() == null
                        ? null
                        : normalized.scopeType().name());
        params.put("fileIds", normalized == null || normalized.fileIds() == null ? List.of() : normalized.fileIds());
        if (normalized != null && normalized.eventPayload() != null) {
            params.put("eventPayload", normalized.eventPayload());
        }
        return new ChatRuntimePreparedRequest(
                sessionType,
                normalized == null ? LingzRuntimeScopeType.GENERAL : normalized.scopeType(),
                normalized == null ? null : normalized.sessionId(),
                normalized == null ? null : normalized.scopeId(),
                null,
                resolvedMessage,
                resolvedMessage,
                normalized == null
                        ? "normal"
                        : chatRuntimePreparedRequestAssembler.resolveRuntimeMessageType(normalized.messageType()),
                sessionType.name(),
                JSON.toJSONString(params),
                null,
                List.of(),
                null,
                normalized == null ? null : normalized.systemPromptAppend(),
                normalized == null ? null : normalized.runtimeSkillName(),
                List.of(),
                List.of(),
                normalized == null ? null : normalized.chatModelId(),
                normalized != null && normalized.isPersonalAgentRequest(),
                normalized == null ? "" : normalized.resolvePersonalAgentMode());
    }

    private String resolveFallbackMessage(LingzRuntimeRequest normalized) {
        if (normalized == null) {
            return "[系统异常前置失败]";
        }
        String message = String.valueOf(normalized.message() == null ? "" : normalized.message())
                .trim();
        if (!message.isEmpty()) {
            return message;
        }
        if (normalized.fileIds() != null && !normalized.fileIds().isEmpty()) {
            return "[仅附件请求]";
        }
        return "[空请求]";
    }

    private String normalizeRequired(String value, String message) throws TaskException {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    public record CancelRunRequest(String reason) {}

    public record CancelRunResponse(boolean accepted, String runCode, String status, String message) {}

    public record ApprovalDecisionRequest(String comment) {}
}
