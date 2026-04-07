package lingzhou.agent.backend.business.chat.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.domain.vo.ChatMessageVo;
import lingzhou.agent.backend.business.chat.domain.vo.ChatSessionVo;
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
    public Map<String, Object> listSessions(
            @RequestParam(value = "sessionType", required = false) String sessionType,
            @RequestParam(value = "scopeId", required = false) Long scopeId,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        Long userId = resolveUserId(request);
        int safeLimit = limit == null ? 20 : limit;
        ConversationSessionType type = parseOptionalSessionType(sessionType);
        List<ChatSessionVo> sessions = conversationHistoryService.listSessions(userId, type, scopeId, safeLimit);

        Map<String, Object> data = new HashMap<>();
        data.put("items", sessions);
        data.put("hasMore", Boolean.FALSE);
        data.put("nextCursor", null);
        return data;
    }

    @GetMapping("/{sessionCode}/messages")
    public Map<String, Object> listMessages(
            @PathVariable("sessionCode") String sessionCode,
            @RequestParam(value = "sessionType", required = false) String sessionType,
            @RequestParam(value = "scopeId", required = false) Long scopeId,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request) {
        Long userId = resolveUserId(request);
        int safePageNo = pageNo == null ? 1 : pageNo;
        int safePageSize = pageSize == null ? 50 : pageSize;
        ConversationSessionType type = parseSessionType(sessionType);

        List<ChatMessageVo> items = conversationHistoryService.listMessages(
                userId, type, sessionCode, scopeId, safePageNo, safePageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("pageNo", safePageNo);
        data.put("pageSize", safePageSize);
        data.put("total", items.size());
        return data;
    }

    @DeleteMapping("/{sessionCode}")
    public Map<String, Object> deleteSession(
            @PathVariable("sessionCode") String sessionCode,
            @RequestParam(value = "sessionType", required = false) String sessionType,
            @RequestParam(value = "scopeId", required = false) Long scopeId,
            HttpServletRequest request) {
        Long userId = resolveUserId(request);
        ConversationSessionType type = parseSessionType(sessionType);

        ConversationHistoryService.DeleteResult result =
                conversationHistoryService.deleteSession(userId, type, sessionCode, scopeId);

        Map<String, Object> data = new HashMap<>();
        data.put("success", result.success());
        data.put("alreadyDeleted", result.alreadyDeleted());
        data.put("affectedSessions", result.affectedSessions());
        data.put("affectedMessages", result.affectedMessages());
        return data;
    }

    @PutMapping("/{sessionCode}/name")
    public Map<String, Object> renameSession(
            @PathVariable("sessionCode") String sessionCode,
            @RequestParam(value = "sessionType", required = false) String sessionType,
            @RequestParam(value = "scopeId", required = false) Long scopeId,
            @RequestBody(required = false) RenameSessionRequest requestBody,
            HttpServletRequest request)
            throws TaskException {
        Long userId = resolveUserId(request);
        ConversationSessionType type = parseSessionType(sessionType);
        ConversationHistoryService.RenameResult result = conversationHistoryService.renameSession(
                userId,
                type,
                sessionCode,
                scopeId,
                requestBody == null ? null : requestBody.name());

        Map<String, Object> data = new HashMap<>();
        data.put("success", Boolean.TRUE);
        data.put("sessionCode", result.sessionCode());
        data.put("name", result.name());
        data.put("title", result.title());
        return data;
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

    private record RenameSessionRequest(String name) {}
}
