package lingzhou.agent.backend.business.chatv2.controller;

import jakarta.servlet.http.HttpServletRequest;
import lingzhou.agent.backend.business.chat.service.ChatConversationService;
import lingzhou.agent.backend.business.chatv2.service.ChatConversationV2Service;
import lingzhou.agent.backend.capability.agentruntime.approval.RuntimeApprovalService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class GeneralChatV2Controller {

    private final ChatConversationV2Service chatConversationV2Service;

    public GeneralChatV2Controller(ChatConversationV2Service chatConversationV2Service) {
        this.chatConversationV2Service = chatConversationV2Service;
    }

    @PostMapping(value = "/chat/v2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestBody(required = false) ChatConversationService.GeneralChatRequest request,
            HttpServletRequest httpRequest) {
        Long userId = chatConversationV2Service.resolveUserId(httpRequest);
        return chatConversationV2Service.streamGeneral(request, userId);
    }

    @PostMapping("/chat/v2/runs/{runCode}/cancel")
    public ChatConversationV2Service.CancelRunResponse cancelRun(
            @PathVariable("runCode") String runCode,
            @RequestBody(required = false) ChatConversationV2Service.CancelRunRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        Long userId = chatConversationV2Service.resolveUserId(httpRequest);
        return chatConversationV2Service.cancelRun(runCode, userId, request);
    }

    @PostMapping("/chat/v2/runs/{runCode}/approvals/{approvalCode}/approve")
    public RuntimeApprovalService.ApprovalDecisionResponse approveRun(
            @PathVariable("runCode") String runCode,
            @PathVariable("approvalCode") String approvalCode,
            @RequestBody(required = false) ChatConversationV2Service.ApprovalDecisionRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        Long userId = chatConversationV2Service.resolveUserId(httpRequest);
        return chatConversationV2Service.approveRun(runCode, approvalCode, userId, request);
    }

    @PostMapping("/chat/v2/runs/{runCode}/approvals/{approvalCode}/reject")
    public RuntimeApprovalService.ApprovalDecisionResponse rejectRun(
            @PathVariable("runCode") String runCode,
            @PathVariable("approvalCode") String approvalCode,
            @RequestBody(required = false) ChatConversationV2Service.ApprovalDecisionRequest request,
            HttpServletRequest httpRequest)
            throws TaskException {
        Long userId = chatConversationV2Service.resolveUserId(httpRequest);
        return chatConversationV2Service.rejectRun(runCode, approvalCode, userId, request);
    }
}
