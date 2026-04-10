package lingzhou.agent.backend.business.chat.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.ChatMessage;
import lingzhou.agent.backend.business.chat.domain.ChatSession;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.domain.vo.ChatMessageVo;
import lingzhou.agent.backend.business.chat.domain.vo.ChatSessionVo;
import lingzhou.agent.backend.business.chat.mapper.ChatMessageMapper;
import lingzhou.agent.backend.business.chat.mapper.ChatSessionMapper;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.business.chat.util.UlidGenerator;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ConversationHistoryService {

    private static final String SESSION_STATUS_NORMAL = "normal";
    private static final String MESSAGE_STATUS_PENDING = "pending";
    private static final String MESSAGE_STATUS_NORMAL = "normal";
    private static final String MESSAGE_STATUS_ERROR = "error";
    private static final String MESSAGE_STATUS_INTERRUPTED = "interrupted";
    private static final String MESSAGE_TYPE_NORMAL = "normal";
    private static final String DEFAULT_SESSION_NAME = "新会话";
    private static final int MAX_LAST_MESSAGE_LENGTH = 120;
    private static final int MAX_AUTO_SESSION_NAME_LENGTH = 24;
    private static final int MAX_CUSTOM_SESSION_NAME_LENGTH = 50;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMemory chatMemory;
    private final ChatFileService chatFileService;
    private final SkillCatalogService skillCatalogService;
    private final IntegrationDatasetService integrationDatasetService;

    public ConversationHistoryService(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMemory chatMemory,
            ChatFileService chatFileService,
            SkillCatalogService skillCatalogService,
            IntegrationDatasetService integrationDatasetService) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMemory = chatMemory;
        this.chatFileService = chatFileService;
        this.skillCatalogService = skillCatalogService;
        this.integrationDatasetService = integrationDatasetService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ConversationContext startMessage(
            Long userId,
            ConversationSessionType sessionType,
            String requestedSessionCode,
            Long scopeId,
            String scopeDisplayName,
            String query,
            String messageType,
            String finalQuery,
            String questionType,
            String paramsJson,
            String fileListJson) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query is required");
        }

        ChatSession session = resolveOrCreateSession(userId, sessionType, requestedSessionCode, scopeId);

        int nextOrdinal = chatMessageMapper.countBySessionId(session.getId()) + 1;
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setQuery(query);
        message.setMessageType(normalizeMessageType(messageType));
        message.setFinalQuery(StringUtils.hasText(finalQuery) ? finalQuery : query);
        message.setQuestionType(questionType);
        message.setParamsJson(paramsJson);
        message.setFileList(fileListJson);
        message.setOrdinal(nextOrdinal);
        message.setStatus(MESSAGE_STATUS_PENDING);
        message.setCreateUserId(userId);
        chatMessageMapper.insert(message);

        return new ConversationContext(
                session.getId(),
                session.getSessionCode(),
                session.getSessionType(),
                session.getScopeId(),
                message.getId(),
                message.getOrdinal() == null ? nextOrdinal : message.getOrdinal(),
                scopeDisplayName,
                query,
                DEFAULT_SESSION_NAME.equals(session.getName()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeMessage(
            ConversationContext context,
            String answer,
            String documentListJson,
            String fileListJson,
            String paramsJson,
            long consumeMillis) {
        if (context == null || context.messageId() == null) {
            return;
        }

        String normalizedAnswer = normalizeText(answer);
        chatMessageMapper.updateSucceeded(
                context.messageId(),
                normalizedAnswer,
                documentListJson,
                fileListJson,
                paramsJson,
                consumeMillis + "ms",
                MESSAGE_STATUS_NORMAL);

        String sessionName = null;
        if (context.messageOrdinal() == 1 && context.usingDefaultName()) {
            sessionName = summarizeSessionName(context.query());
        }

        String lastMessage = toLastMessage(normalizedAnswer, context.query());
        chatSessionMapper.updateSessionSnapshot(context.sessionId(), lastMessage, sessionName, SESSION_STATUS_NORMAL);
    }

    @Transactional(rollbackFor = Exception.class)
    public void failMessage(
            ConversationContext context,
            String errorMessage,
            String partialAnswer,
            String paramsJson,
            long consumeMillis) {
        if (context == null || context.messageId() == null) {
            return;
        }
        String answer = normalizeText(partialAnswer);
        String error = normalizeText(errorMessage);
        chatMessageMapper.updateFailed(
                context.messageId(),
                error,
                consumeMillis + "ms",
                MESSAGE_STATUS_ERROR,
                answer,
                paramsJson);
        chatSessionMapper.updateSessionSnapshot(
                context.sessionId(),
                toLastMessage(answer, context.query()),
                null,
                SESSION_STATUS_NORMAL);
    }

    @Transactional(rollbackFor = Exception.class)
    public void interruptMessage(
            ConversationContext context, String partialAnswer, String paramsJson, long consumeMillis) {
        if (context == null || context.messageId() == null) {
            return;
        }
        String answer = normalizeText(partialAnswer);
        chatMessageMapper.updateFailed(
                context.messageId(),
                "stream interrupted",
                consumeMillis + "ms",
                MESSAGE_STATUS_INTERRUPTED,
                answer,
                paramsJson);
        chatSessionMapper.updateSessionSnapshot(
                context.sessionId(),
                toLastMessage(answer, context.query()),
                null,
                SESSION_STATUS_NORMAL);
    }

    public List<ChatSessionVo> listSessions(Long userId, ConversationSessionType sessionType, Long scopeId, int limit) {
        if (userId == null || userId <= 0) {
            return List.of();
        }

        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<ChatSession> sessions;
        if (sessionType == null) {
            sessions = chatSessionMapper.selectRecentSessionsByTypes(
                    userId,
                    List.of(
                            ConversationSessionType.GENERAL_CHAT.name(),
                            ConversationSessionType.SKILL_CHAT.name()),
                    safeLimit);
        } else {
            sessions = chatSessionMapper.selectRecentSessions(userId, sessionType.name(), scopeId, safeLimit);
        }
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<ChatSessionVo> items = new ArrayList<>(sessions.size());
        for (ChatSession session : sessions) {
            String scopeDisplayName = resolveScopeDisplayName(session.getSessionType(), session.getScopeId());
            ChatSessionVo item = new ChatSessionVo();
            item.setId(session.getSessionCode());
            item.setName(resolveEditableSessionName(session, scopeDisplayName));
            item.setTitle(resolveSessionTitle(session, scopeDisplayName));
            item.setActive(Boolean.FALSE);
            item.setLastMessage(session.getLastMessage());
            item.setSessionType(session.getSessionType());
            item.setScopeId(session.getScopeId());
            item.setScopeDisplayName(scopeDisplayName);
            Date updatedAt = session.getUpdatedAt() != null ? session.getUpdatedAt() : session.getCreatedAt();
            item.setUpdatedAt(updatedAt == null ? null : format.format(updatedAt));
            items.add(item);
        }
        return items;
    }

    public List<ChatMessageVo> listMessages(
            Long userId,
            ConversationSessionType sessionType,
            String sessionCode,
            Long scopeId,
            int pageNo,
            int pageSize) {
        ChatSession session = findOwnedSession(userId, sessionType, sessionCode, scopeId);
        if (session == null) {
            return List.of();
        }

        List<ChatMessage> rows = chatMessageMapper.selectBySessionId(session.getId(), pageNo, pageSize);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Collections.reverse(rows);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<ChatMessageVo> items = new ArrayList<>(rows.size());
        for (ChatMessage row : rows) {
            ChatMessageVo item = new ChatMessageVo();
            item.setId(row.getId());
            item.setQuery(row.getQuery());
            item.setMessageType(row.getMessageType());
            item.setAnswer(row.getAnswer());
            item.setStatus(row.getStatus());
            item.setError(row.getError());
            item.setDocumentList(row.getDocumentList());
            item.setFileList(row.getFileList());
            item.setParamsJson(row.getParamsJson());
            item.setQuestionType(row.getQuestionType());
            item.setCreatedAt(row.getCreatedAt() == null ? null : format.format(row.getCreatedAt()));
            item.setUpdatedAt(row.getUpdatedAt() == null ? null : format.format(row.getUpdatedAt()));
            items.add(item);
        }
        return items;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeleteResult deleteSession(
            Long userId, ConversationSessionType sessionType, String sessionCode, Long scopeId) {
        ChatSession session = findOwnedSession(userId, sessionType, sessionCode, scopeId);
        if (session == null) {
            return new DeleteResult(true, true, 0, 0);
        }

        chatFileService.deletePersistedFiles(chatMessageMapper.selectFileListsBySessionId(session.getId()));
        clearMemory(session.getSessionCode());

        int affectedMessages = chatMessageMapper.deleteBySessionId(session.getId());
        int affectedSessions = chatSessionMapper.deleteBySessionCode(userId, sessionType.name(), session.getSessionCode());
        return new DeleteResult(true, false, affectedSessions, affectedMessages);
    }

    @Transactional(rollbackFor = Exception.class)
    public RenameResult renameSession(
            Long userId, ConversationSessionType sessionType, String sessionCode, Long scopeId, String name)
            throws TaskException {
        ChatSession session = findOwnedSession(userId, sessionType, sessionCode, scopeId);
        if (session == null) {
            throw new TaskException("会话不存在或无权修改", TaskException.Code.UNKNOWN);
        }

        String normalizedName = normalizeSessionName(name);
        chatSessionMapper.updateSessionName(session.getId(), normalizedName);

        String scopeDisplayName = resolveScopeDisplayName(session.getSessionType(), session.getScopeId());
        return new RenameResult(
                session.getSessionCode(),
                normalizedName,
                decorateSessionTitle(normalizedName, session.getSessionType(), scopeDisplayName));
    }

    private ChatSession findOwnedSession(
            Long userId, ConversationSessionType sessionType, String sessionCode, Long scopeId) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(sessionCode)) {
            return null;
        }
        return chatSessionMapper.selectBySessionCode(userId, sessionType.name(), sessionCode.trim(), scopeId);
    }

    private ChatSession resolveOrCreateSession(
            Long userId, ConversationSessionType sessionType, String requestedSessionCode, Long scopeId) {
        ChatSession existing = null;
        if (StringUtils.hasText(requestedSessionCode)) {
            existing = chatSessionMapper.selectBySessionCode(
                    userId,
                    sessionType.name(),
                    requestedSessionCode.trim(),
                    scopeId);
        }
        if (existing != null) {
            return existing;
        }

        ChatSession created = new ChatSession();
        created.setSessionCode(nextSessionCode());
        created.setSessionType(sessionType.name());
        created.setScopeId(scopeId);
        created.setName(DEFAULT_SESSION_NAME);
        created.setStatus(SESSION_STATUS_NORMAL);
        created.setCreateUserId(userId);
        chatSessionMapper.insert(created);
        return created;
    }

    private void clearMemory(String sessionCode) {
        try {
            chatMemory.clear(sessionCode);
        } catch (Exception ex) {
            log.error("清理会话记忆失败：sessionCode={}, error={}", sessionCode, ex.getMessage(), ex);
            throw new IllegalStateException("清理会话记忆失败，请稍后重试");
        }
    }

    private String nextSessionCode() {
        for (int i = 0; i < 5; i++) {
            String code = UlidGenerator.next();
            ChatSession existing = chatSessionMapper.selectBySessionCodeGlobal(code);
            if (existing == null) {
                return code;
            }
        }
        return UlidGenerator.next();
    }

    private String toLastMessage(String answer, String query) {
        String preferred = StringUtils.hasText(answer) ? answer : query;
        if (!StringUtils.hasText(preferred)) {
            return "";
        }
        String value = preferred.trim();
        return value.length() <= MAX_LAST_MESSAGE_LENGTH ? value : value.substring(0, MAX_LAST_MESSAGE_LENGTH);
    }

    private String summarizeSessionName(String query) {
        if (!StringUtils.hasText(query)) {
            return DEFAULT_SESSION_NAME;
        }
        String text = query.trim();
        return text.length() <= MAX_AUTO_SESSION_NAME_LENGTH
                ? text
                : text.substring(0, MAX_AUTO_SESSION_NAME_LENGTH);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String normalizeMessageType(String value) {
        String normalized = normalizeText(value);
        return StringUtils.hasText(normalized) ? normalized : MESSAGE_TYPE_NORMAL;
    }

    private String normalizeTextValue(Object value) {
        return value == null ? "" : normalizeText(String.valueOf(value));
    }

    private String resolveScopeDisplayName(String sessionType, Long scopeId) {
        if (scopeId == null || scopeId <= 0) {
            return "";
        }
        if (ConversationSessionType.SKILL_CHAT.name().equals(sessionType)) {
            return skillCatalogService.resolveSkillDisplayName(scopeId);
        }
        if (ConversationSessionType.DATASET_CHAT.name().equals(sessionType)) {
            try {
                return integrationDatasetService.getDataset(scopeId).name();
            } catch (Exception ignored) {
                return "";
            }
        }
        return "";
    }

    private String resolveSessionTitle(ChatSession session, String scopeDisplayName) {
        String baseTitle = StringUtils.hasText(session.getName()) ? session.getName().trim() : DEFAULT_SESSION_NAME;
        return decorateSessionTitle(baseTitle, session.getSessionType(), scopeDisplayName);
    }

    private String resolveEditableSessionName(ChatSession session, String scopeDisplayName) {
        String rawName = StringUtils.hasText(session.getName()) ? session.getName().trim() : DEFAULT_SESSION_NAME;
        if ((!ConversationSessionType.SKILL_CHAT.name().equals(session.getSessionType())
                        && !ConversationSessionType.DATASET_CHAT.name().equals(session.getSessionType()))
                || !StringUtils.hasText(scopeDisplayName)) {
            return rawName;
        }
        String prefix = scopeDisplayName.trim() + " · ";
        if (rawName.startsWith(prefix)) {
            return rawName.substring(prefix.length()).trim();
        }
        if (rawName.equals(scopeDisplayName.trim())) {
            return DEFAULT_SESSION_NAME;
        }
        return rawName;
    }

    private String normalizeSessionName(String value) throws TaskException {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            throw new TaskException("会话名称不能为空", TaskException.Code.UNKNOWN);
        }
        return normalized.length() <= MAX_CUSTOM_SESSION_NAME_LENGTH
                ? normalized
                : normalized.substring(0, MAX_CUSTOM_SESSION_NAME_LENGTH);
    }

    private String decorateSessionTitle(String baseTitle, String sessionType, String scopeDisplayName) {
        String normalizedTitle = StringUtils.hasText(baseTitle) ? baseTitle.trim() : DEFAULT_SESSION_NAME;
        if ((!ConversationSessionType.SKILL_CHAT.name().equals(sessionType)
                        && !ConversationSessionType.DATASET_CHAT.name().equals(sessionType))
                || !StringUtils.hasText(scopeDisplayName)) {
            return normalizedTitle;
        }
        if (DEFAULT_SESSION_NAME.equals(normalizedTitle)) {
            return scopeDisplayName.trim();
        }
        String prefix = scopeDisplayName.trim() + " · ";
        if (normalizedTitle.startsWith(prefix) || normalizedTitle.equals(scopeDisplayName.trim())) {
            return normalizedTitle;
        }
        return prefix + normalizedTitle;
    }

    public record ConversationContext(
            Long sessionId,
            String sessionCode,
            String sessionType,
            Long scopeId,
            Long messageId,
            Integer messageOrdinal,
            String scopeDisplayName,
            String query,
            boolean usingDefaultName) {}

    public record DeleteResult(boolean success, boolean alreadyDeleted, int affectedSessions, int affectedMessages) {}

    public record RenameResult(String sessionCode, String name, String title) {}

    public Map<String, Object> buildMetaPayload(ConversationContext context) {
        return Map.of(
                "sessionId", context.sessionCode(),
                "messageId", context.messageId(),
                "sessionType", context.sessionType(),
                "scopeId", context.scopeId() == null ? "" : context.scopeId(),
                "scopeDisplayName", context.scopeDisplayName() == null ? "" : context.scopeDisplayName());
    }
}
