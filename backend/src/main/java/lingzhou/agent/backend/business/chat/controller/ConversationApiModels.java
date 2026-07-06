package lingzhou.agent.backend.business.chat.controller;

import java.util.List;
import lingzhou.agent.backend.business.chat.domain.vo.ChatMessageVo;
import lingzhou.agent.backend.business.chat.domain.vo.ChatSessionVo;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;

public final class ConversationApiModels {

    private ConversationApiModels() {}

    public record SessionListResponse(
            List<ChatSessionVo> items,
            Boolean hasMore,
            String nextCursor,
            Integer pageNo,
            Integer pageSize,
            Integer total,
            Integer nextPageNo) {}

    public record MessageListResponse(
            List<ChatMessageVo> items,
            Integer pageNo,
            Integer pageSize,
            Integer total,
            Boolean hasMore,
            Integer nextPageNo) {}

    public record DeleteSessionResponse(
            Boolean success, Boolean alreadyDeleted, Integer affectedSessions, Integer affectedMessages) {}

    public record RenameSessionRequest(String name) {}

    public record RenameSessionResponse(Boolean success, String sessionId, String name, String title) {}

    public record UpdateSessionChatModelRequest(Long modelId) {}

    public record UpdateSessionChatModelResponse(
            Boolean success,
            String sessionId,
            Long chatModelId,
            String chatModelDisplayName,
            Boolean chatModelAvailable) {}

    public static SessionListResponse sessionList(List<ChatSessionVo> items) {
        return sessionList(items, 1, items == null ? 0 : items.size(), items == null ? 0 : items.size());
    }

    public static SessionListResponse sessionList(List<ChatSessionVo> items, int pageNo, int pageSize, int total) {
        int safeTotal = Math.max(0, total);
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        boolean hasMore = safePageNo * safePageSize < safeTotal;
        return new SessionListResponse(
                items, hasMore, null, safePageNo, safePageSize, safeTotal, hasMore ? safePageNo + 1 : null);
    }

    public static MessageListResponse messageList(List<ChatMessageVo> items, int pageNo, int pageSize) {
        int itemCount = items == null ? 0 : items.size();
        boolean hasMore = itemCount >= pageSize;
        return new MessageListResponse(items, pageNo, pageSize, itemCount, hasMore, hasMore ? pageNo + 1 : null);
    }

    public static MessageListResponse messageList(List<ChatMessageVo> items, int pageNo, int pageSize, int total) {
        int safeTotal = Math.max(0, total);
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        boolean hasMore = safePageNo * safePageSize < safeTotal;
        return new MessageListResponse(items, safePageNo, safePageSize, safeTotal, hasMore, hasMore ? safePageNo + 1 : null);
    }

    public static DeleteSessionResponse deleteResult(ConversationHistoryService.DeleteResult result) {
        return new DeleteSessionResponse(
                result.success(), result.alreadyDeleted(), result.affectedSessions(), result.affectedMessages());
    }

    public static RenameSessionResponse renameResult(ConversationHistoryService.RenameResult result) {
        return new RenameSessionResponse(Boolean.TRUE, result.sessionId(), result.name(), result.title());
    }

    public static UpdateSessionChatModelResponse updateChatModelResult(
            ConversationHistoryService.UpdateSessionChatModelResult result) {
        return new UpdateSessionChatModelResponse(
                Boolean.TRUE,
                result.sessionId(),
                result.chatModelId(),
                result.chatModelDisplayName(),
                result.chatModelAvailable());
    }
}
