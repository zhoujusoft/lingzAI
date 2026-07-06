package lingzhou.agent.backend.business.chat.controller;

import jakarta.servlet.http.HttpServletRequest;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat/sessions")
public class ConversationController {

    private final ConversationHistoryService conversationHistoryService;

    public ConversationController(ConversationHistoryService conversationHistoryService) {
        this.conversationHistoryService = conversationHistoryService;
    }

    @GetMapping
    public ConversationApiModels.SessionListResponse listSessions(
            @RequestParam(value = "sessionType", required = false) String sessionType,
            @RequestParam(value = "scopeId", required = false) Long scopeId,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request) {
        Long userId = resolveUserId(request);
        int safePageNo = pageNo == null ? 1 : pageNo;
        int safePageSize = normalizeSessionPageSize(pageSize == null ? limit : pageSize);
        ConversationSessionType type = parseOptionalSessionType(sessionType);
        ConversationHistoryService.SessionPageResult page =
                conversationHistoryService.listSessions(userId, type, scopeId, safePageNo, safePageSize);
        return ConversationApiModels.sessionList(page.items(), safePageNo, safePageSize, page.total());
    }

    @GetMapping("/{sessionId}/messages")
    public ConversationApiModels.MessageListResponse listMessages(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "sessionType", required = false) String sessionType,
            @RequestParam(value = "scopeId", required = false) Long scopeId,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request) {
        Long userId = resolveUserId(request);
        int safePageNo = pageNo == null ? 1 : pageNo;
        int safePageSize = normalizeMessagePageSize(pageSize);
        ConversationSessionType type = parseSessionType(sessionType);

        ConversationHistoryService.MessagePageResult page =
                conversationHistoryService.listMessages(userId, type, sessionId, scopeId, safePageNo, safePageSize);
        return ConversationApiModels.messageList(page.items(), safePageNo, safePageSize, page.total());
    }

    @DeleteMapping("/{sessionId}")
    public ConversationApiModels.DeleteSessionResponse deleteSession(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "sessionType", required = false) String sessionType,
            @RequestParam(value = "scopeId", required = false) Long scopeId,
            HttpServletRequest request) {
        Long userId = resolveUserId(request);
        ConversationSessionType type = parseSessionType(sessionType);

        ConversationHistoryService.DeleteResult result =
                conversationHistoryService.deleteSession(userId, type, sessionId, scopeId);
        return ConversationApiModels.deleteResult(result);
    }

    @PutMapping("/{sessionId}/name")
    public ConversationApiModels.RenameSessionResponse renameSession(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "sessionType", required = false) String sessionType,
            @RequestParam(value = "scopeId", required = false) Long scopeId,
            @RequestBody(required = false) ConversationApiModels.RenameSessionRequest requestBody,
            HttpServletRequest request)
            throws TaskException {
        Long userId = resolveUserId(request);
        ConversationSessionType type = parseSessionType(sessionType);
        ConversationHistoryService.RenameResult result = conversationHistoryService.renameSession(
                userId, type, sessionId, scopeId, requestBody == null ? null : requestBody.name());
        return ConversationApiModels.renameResult(result);
    }

    @PutMapping("/{sessionId}/model")
    public ConversationApiModels.UpdateSessionChatModelResponse updateSessionChatModel(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "sessionType", required = false) String sessionType,
            @RequestParam(value = "scopeId", required = false) Long scopeId,
            @RequestBody(required = false) ConversationApiModels.UpdateSessionChatModelRequest requestBody,
            HttpServletRequest request)
            throws TaskException {
        Long userId = resolveUserId(request);
        ConversationSessionType type = parseSessionType(sessionType);
        ConversationHistoryService.UpdateSessionChatModelResult result =
                conversationHistoryService.updateSessionChatModel(
                        userId, type, sessionId, scopeId, requestBody == null ? null : requestBody.modelId());
        return ConversationApiModels.updateChatModelResult(result);
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object value = request.getAttribute("UserId");
        if (value == null) {
            throw new IllegalStateException("UserId missing");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private ConversationSessionType parseSessionType(String value) {
        try {
            return ConversationSessionType.fromValue(value);
        } catch (Exception ignored) {
            return ConversationSessionType.SKILL_CHAT;
        }
    }

    private ConversationSessionType parseOptionalSessionType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return parseSessionType(value);
    }

    private int normalizeMessagePageSize(Integer pageSize) {
        if (pageSize == null) {
            return 10;
        }
        return Math.max(1, Math.min(pageSize, 100));
    }

    private int normalizeSessionPageSize(Integer pageSize) {
        if (pageSize == null) {
            return 20;
        }
        return Math.max(1, Math.min(pageSize, 100));
    }
}
