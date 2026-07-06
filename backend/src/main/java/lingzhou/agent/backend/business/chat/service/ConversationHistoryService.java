package lingzhou.agent.backend.business.chat.service;

import com.alibaba.fastjson.JSON;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.channel.domain.ChannelSessionBinding;
import lingzhou.agent.backend.business.channel.mapper.ChannelSessionBindingMapper;
import lingzhou.agent.backend.business.chat.domain.ConversationMessage;
import lingzhou.agent.backend.business.chat.domain.ConversationSession;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.domain.vo.ChatMessageVo;
import lingzhou.agent.backend.business.chat.domain.vo.ChatSessionVo;
import lingzhou.agent.backend.business.chat.mapper.ConversationMessageMapper;
import lingzhou.agent.backend.business.chat.mapper.ConversationSessionMapper;
import lingzhou.agent.backend.business.chat.util.UlidGenerator;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetService;
import lingzhou.agent.backend.business.model.service.ModelLibraryService;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.business.system.model.AgentDetailDto;
import lingzhou.agent.backend.business.system.service.AgentTemplateService;
import lingzhou.agent.backend.common.lzException.TaskException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ConversationHistoryService {

    private static final String SESSION_STATUS_ACTIVE = "ACTIVE";
    private static final String MESSAGE_STATUS_PENDING = "PENDING";
    private static final String MESSAGE_STATUS_STREAMING = "STREAMING";
    private static final String MESSAGE_STATUS_COMPLETED = "COMPLETED";
    private static final String MESSAGE_STATUS_FAILED = "FAILED";
    private static final String MESSAGE_STATUS_CANCELLED = "CANCELLED";
    private static final String MESSAGE_STATUS_WAITING_APPROVAL = "WAITING_APPROVAL";
    private static final String DEFAULT_SESSION_NAME = "新会话";
    private static final int MAX_LAST_MESSAGE_LENGTH = 120;
    private static final int MAX_AUTO_SESSION_NAME_LENGTH = 24;
    private static final int MAX_CUSTOM_SESSION_NAME_LENGTH = 50;

    private final ConversationSessionMapper conversationSessionMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final ConversationRunUsageService conversationRunUsageService;
    private final ConversationEventService conversationEventService;
    private final ChatFileService chatFileService;
    private final ChannelSessionBindingMapper channelSessionBindingMapper;
    private final SkillCatalogService skillCatalogService;
    private final IntegrationDatasetService integrationDatasetService;
    private final AgentTemplateService agentTemplateService;
    private final ModelLibraryService modelLibraryService;
    private final RuntimeFileAssetService runtimeFileAssetService;

    public ConversationHistoryService(
            ConversationSessionMapper conversationSessionMapper,
            ConversationMessageMapper conversationMessageMapper,
            ConversationRunUsageService conversationRunUsageService,
            ConversationEventService conversationEventService,
            ChatFileService chatFileService,
            ChannelSessionBindingMapper channelSessionBindingMapper,
            SkillCatalogService skillCatalogService,
            IntegrationDatasetService integrationDatasetService,
            AgentTemplateService agentTemplateService,
            ModelLibraryService modelLibraryService,
            RuntimeFileAssetService runtimeFileAssetService) {
        this.conversationSessionMapper = conversationSessionMapper;
        this.conversationMessageMapper = conversationMessageMapper;
        this.conversationRunUsageService = conversationRunUsageService;
        this.conversationEventService = conversationEventService;
        this.chatFileService = chatFileService;
        this.channelSessionBindingMapper = channelSessionBindingMapper;
        this.skillCatalogService = skillCatalogService;
        this.integrationDatasetService = integrationDatasetService;
        this.agentTemplateService = agentTemplateService;
        this.modelLibraryService = modelLibraryService;
        this.runtimeFileAssetService = runtimeFileAssetService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ConversationContext startMessage(
            Long userId,
            ConversationSessionType sessionType,
            String requestedSessionCode,
            Long scopeId,
            String scopeDisplayName,
            String message,
            String messageType,
            String finalMessage,
            String questionType,
            String paramsJson,
            String fileListJson,
            Long requestedChatModelId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message is required");
        }

        ConversationSession session =
                resolveOrCreateSession(userId, sessionType, requestedSessionCode, scopeId, requestedChatModelId);
        runtimeFileAssetService.bindUploadsToSession(userId, session.getSessionCode(), extractFileIds(fileListJson));
        int nextSequence = conversationMessageMapper.countBySessionId(session.getId()) + 1;

        ConversationMessage userMessage = new ConversationMessage();
        userMessage.setSessionId(session.getId());
        userMessage.setMessageCode(nextResourceCode());
        userMessage.setRole("USER");
        userMessage.setMessageKind("INPUT");
        userMessage.setContent(message.trim());
        userMessage.setContentFormat("TEXT");
        userMessage.setStatus(MESSAGE_STATUS_COMPLETED);
        userMessage.setParamsJson(buildUserMessageParamsJson(messageType, finalMessage, questionType, paramsJson));
        userMessage.setAttachmentsJson(normalizeNullableText(fileListJson));
        userMessage.setSequenceNo(nextSequence);
        userMessage.setCreateUserId(userId);
        userMessage.setCompletedAt(new Date());
        conversationMessageMapper.insert(userMessage);

        ConversationMessage assistantMessage = new ConversationMessage();
        assistantMessage.setSessionId(session.getId());
        assistantMessage.setMessageCode(nextResourceCode());
        assistantMessage.setParentMessageId(userMessage.getId());
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setMessageKind("REPLY");
        assistantMessage.setContent("");
        assistantMessage.setContentFormat("MARKDOWN");
        assistantMessage.setStatus(MESSAGE_STATUS_PENDING);
        assistantMessage.setSequenceNo(nextSequence + 1);
        assistantMessage.setCreateUserId(userId);
        conversationMessageMapper.insert(assistantMessage);

        ConversationContext context = new ConversationContext(
                session.getId(),
                session.getSessionCode(),
                session.getSessionType(),
                session.getScopeId(),
                userMessage.getId(),
                assistantMessage.getId(),
                userMessage.getSequenceNo(),
                assistantMessage.getSequenceNo(),
                userId,
                scopeDisplayName,
                message.trim(),
                DEFAULT_SESSION_NAME.equals(session.getName()));
        conversationEventService.appendEvent(
                context,
                userMessage.getId(),
                "USER_MESSAGE_CREATED",
                null,
                shrinkForSummary(message, MAX_LAST_MESSAGE_LENGTH),
                buildUserMessageEventPayload(messageType, finalMessage, questionType, paramsJson, fileListJson));
        conversationEventService.appendEvent(
                context,
                assistantMessage.getId(),
                "ASSISTANT_MESSAGE_STARTED",
                null,
                "",
                JSON.toJSONString(Map.of("assistantMessageId", assistantMessage.getId())));
        return context;
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeMessage(
            ConversationContext context,
            String answer,
            String documentListJson,
            String fileListJson,
            String paramsJson,
            long consumeMillis) {
        completeMessage(context, answer, null, documentListJson, fileListJson, paramsJson, consumeMillis, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeMessage(
            ConversationContext context,
            String answer,
            String documentListJson,
            String fileListJson,
            String paramsJson,
            long consumeMillis,
            ConversationMessageUsagePayload usagePayload) {
        completeMessage(context, answer, null, documentListJson, fileListJson, paramsJson, consumeMillis, usagePayload);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeMessage(
            ConversationContext context,
            String answer,
            String segmentsJson,
            String documentListJson,
            String fileListJson,
            String paramsJson,
            long consumeMillis,
            ConversationMessageUsagePayload usagePayload) {
        if (context == null || context.assistantMessageId() == null) {
            return;
        }

        String normalizedAnswer = normalizeText(answer);
        conversationMessageMapper.updateAssistantMessage(
                context.assistantMessageId(),
                normalizedAnswer,
                normalizeNullableText(segmentsJson),
                normalizeNullableText(documentListJson),
                normalizeNullableText(paramsJson),
                MESSAGE_STATUS_COMPLETED,
                null,
                null,
                usagePayload);

        String sessionName = null;
        if (context.userMessageSequenceNo() == 1 && context.usingDefaultName()) {
            sessionName = summarizeSessionName(context.message());
        }
        String lastMessage = toLastMessage(normalizedAnswer, context.message());
        conversationSessionMapper.updateSessionSnapshot(
                context.sessionId(), context.assistantMessageId(), lastMessage, sessionName, SESSION_STATUS_ACTIVE);
        conversationEventService.appendEvent(
                context,
                context.assistantMessageId(),
                "MESSAGE_COMPLETED",
                null,
                shrinkForSummary(normalizedAnswer, 240),
                toJson(
                        Map.of("consumeMillis", consumeMillis),
                        "documentListJson",
                        normalizeNullableText(documentListJson),
                        "fileListJson",
                        normalizeNullableText(fileListJson)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void failMessage(
            ConversationContext context,
            String errorMessage,
            String partialAnswer,
            String paramsJson,
            long consumeMillis) {
        failMessage(context, errorMessage, partialAnswer, null, paramsJson, consumeMillis, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void failMessage(
            ConversationContext context,
            String errorMessage,
            String partialAnswer,
            String paramsJson,
            long consumeMillis,
            ConversationMessageUsagePayload usagePayload) {
        failMessage(context, errorMessage, partialAnswer, null, paramsJson, consumeMillis, usagePayload);
    }

    @Transactional(rollbackFor = Exception.class)
    public void failMessage(
            ConversationContext context,
            String errorMessage,
            String partialAnswer,
            String segmentsJson,
            String paramsJson,
            long consumeMillis,
            ConversationMessageUsagePayload usagePayload) {
        if (context == null || context.assistantMessageId() == null) {
            return;
        }
        String answer = normalizeText(partialAnswer);
        String error = normalizeText(errorMessage);
        conversationMessageMapper.updateAssistantMessage(
                context.assistantMessageId(),
                answer,
                normalizeNullableText(segmentsJson),
                null,
                normalizeNullableText(paramsJson),
                MESSAGE_STATUS_FAILED,
                "MESSAGE_FAILED",
                error,
                usagePayload);
        conversationSessionMapper.updateSessionSnapshot(
                context.sessionId(),
                context.assistantMessageId(),
                toLastMessage(answer, context.message()),
                null,
                SESSION_STATUS_ACTIVE);
        conversationEventService.appendEvent(
                context,
                context.assistantMessageId(),
                "MESSAGE_FAILED",
                null,
                error,
                toJson(
                        Map.of("partialAnswer", answer, "consumeMillis", consumeMillis),
                        "paramsJson",
                        normalizeNullableText(paramsJson),
                        null,
                        null));
    }

    @Transactional(rollbackFor = Exception.class)
    public void interruptMessage(
            ConversationContext context, String partialAnswer, String paramsJson, long consumeMillis) {
        interruptMessage(context, partialAnswer, null, paramsJson, consumeMillis, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void interruptMessage(
            ConversationContext context,
            String partialAnswer,
            String paramsJson,
            long consumeMillis,
            ConversationMessageUsagePayload usagePayload) {
        interruptMessage(context, partialAnswer, null, paramsJson, consumeMillis, usagePayload);
    }

    @Transactional(rollbackFor = Exception.class)
    public void interruptMessage(
            ConversationContext context,
            String partialAnswer,
            String segmentsJson,
            String paramsJson,
            long consumeMillis,
            ConversationMessageUsagePayload usagePayload) {
        if (context == null || context.assistantMessageId() == null) {
            return;
        }
        String answer = normalizeText(partialAnswer);
        conversationMessageMapper.updateAssistantMessage(
                context.assistantMessageId(),
                answer,
                normalizeNullableText(segmentsJson),
                null,
                normalizeNullableText(paramsJson),
                MESSAGE_STATUS_CANCELLED,
                "MESSAGE_CANCELLED",
                "stream interrupted",
                usagePayload);
        conversationSessionMapper.updateSessionSnapshot(
                context.sessionId(),
                context.assistantMessageId(),
                toLastMessage(answer, context.message()),
                null,
                SESSION_STATUS_ACTIVE);
        conversationEventService.appendEvent(
                context,
                context.assistantMessageId(),
                "MESSAGE_CANCELLED",
                null,
                shrinkForSummary(answer, 240),
                toJson(
                        Map.of("partialAnswer", answer, "consumeMillis", consumeMillis),
                        "paramsJson",
                        normalizeNullableText(paramsJson),
                        null,
                        null));
    }

    @Transactional(rollbackFor = Exception.class)
    public void waitingApprovalMessage(
            ConversationContext context,
            String partialAnswer,
            String segmentsJson,
            String paramsJson,
            long consumeMillis,
            ConversationMessageUsagePayload usagePayload) {
        if (context == null || context.assistantMessageId() == null) {
            return;
        }
        String answer = normalizeText(partialAnswer);
        conversationMessageMapper.updateAssistantMessage(
                context.assistantMessageId(),
                answer,
                normalizeNullableText(segmentsJson),
                null,
                normalizeNullableText(paramsJson),
                MESSAGE_STATUS_WAITING_APPROVAL,
                null,
                null,
                usagePayload);
        conversationSessionMapper.updateSessionSnapshot(
                context.sessionId(),
                context.assistantMessageId(),
                toLastMessage(answer, context.message()),
                null,
                SESSION_STATUS_ACTIVE);
        conversationEventService.appendEvent(
                context,
                context.assistantMessageId(),
                "MESSAGE_WAITING_APPROVAL",
                null,
                shrinkForSummary(answer, 240),
                toJson(
                        Map.of("partialAnswer", answer, "consumeMillis", consumeMillis),
                        "paramsJson",
                        normalizeNullableText(paramsJson),
                        null,
                        null));
    }

    public List<ChatSessionVo> listSessions(Long userId, ConversationSessionType sessionType, Long scopeId, int limit) {
        return listSessions(userId, sessionType, scopeId, 1, limit).items();
    }

    public SessionPageResult listSessions(
            Long userId, ConversationSessionType sessionType, Long scopeId, int pageNo, int pageSize) {
        if (userId == null || userId <= 0) {
            return new SessionPageResult(List.of(), 0);
        }

        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        List<ConversationSession> sessions;
        int total;
        if (sessionType == null) {
            List<String> sessionTypes = List.of(
                    ConversationSessionType.GENERAL_CHAT.name(),
                    ConversationSessionType.GENERAL_CHAT_V2.name(),
                    ConversationSessionType.SKILL_CHAT.name(),
                    ConversationSessionType.PUBLISHED_SKILL_CHAT.name(),
                    ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT.name(),
                    ConversationSessionType.CHANNEL_CHAT.name());
            total = conversationSessionMapper.countRecentSessionsByTypes(userId, sessionTypes);
            sessions = conversationSessionMapper.selectRecentSessionsByTypes(
                    userId, sessionTypes, safePageNo, safePageSize);
        } else {
            total = conversationSessionMapper.countRecentSessions(userId, sessionType.name(), scopeId);
            sessions = conversationSessionMapper.selectRecentSessions(
                    userId, sessionType.name(), scopeId, safePageNo, safePageSize);
        }
        if (sessions == null || sessions.isEmpty()) {
            return new SessionPageResult(List.of(), total);
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ModelLibraryService.ChatModelReferenceView defaultChatModel =
                modelLibraryService.resolveChatModelReference(null);
        List<ChatSessionVo> items = new ArrayList<>(sessions.size());
        for (ConversationSession session : sessions) {
            String scopeDisplayName = resolveScopeDisplayName(session.getSessionType(), session.getScopeId());
            ModelLibraryService.ChatModelReferenceView chatModel = session.getChatModelId() == null
                    ? defaultChatModel
                    : modelLibraryService.resolveChatModelReference(session.getChatModelId());
            ChatSessionVo item = new ChatSessionVo();
            item.setId(session.getSessionCode());
            item.setName(resolveEditableSessionName(session, scopeDisplayName));
            item.setTitle(resolveSessionTitle(session, scopeDisplayName));
            item.setActive(Boolean.FALSE);
            item.setLastMessage(session.getLastMessage());
            item.setSessionType(session.getSessionType());
            item.setSessionTypeLabel(resolveSessionTypeLabel(session.getSessionType()));
            item.setScopeId(session.getScopeId());
            item.setScopeDisplayName(scopeDisplayName);
            item.setSourceType(resolveSessionSourceType(session.getSessionType()));
            item.setSourceLabel(resolveSessionSourceLabel(session, scopeDisplayName));
            item.setSourceIcon(resolveSessionSourceIcon(session));
            item.setSourceIconColor(resolveSessionSourceIconColor(session));
            item.setChannelType(resolveSessionChannelType(session));
            item.setTitleSummary(resolveSessionTitleSummary(session, scopeDisplayName));
            item.setSubtitle(resolveSessionSubtitle(session, scopeDisplayName));
            item.setChatModelId(chatModel == null ? null : chatModel.id());
            item.setChatModelDisplayName(chatModel == null ? "" : chatModel.displayName());
            item.setChatModelAvailable(chatModel != null && Boolean.TRUE.equals(chatModel.available()));
            Date updatedAt = session.getUpdatedAt() != null ? session.getUpdatedAt() : session.getCreatedAt();
            item.setUpdatedAt(updatedAt == null ? null : format.format(updatedAt));
            items.add(item);
        }
        return new SessionPageResult(items, total);
    }

    public MessagePageResult listMessages(
            Long userId,
            ConversationSessionType sessionType,
            String sessionId,
            Long scopeId,
            int pageNo,
            int pageSize) {
        ConversationSession session = findOwnedSession(userId, sessionType, sessionId, scopeId);
        if (session == null) {
            return new MessagePageResult(List.of(), 0);
        }

        int total = conversationMessageMapper.countBySessionId(session.getId());
        List<ConversationMessage> rows =
                conversationMessageMapper.selectTimelineBySessionId(session.getId(), pageNo, pageSize);
        if (rows == null || rows.isEmpty()) {
            return new MessagePageResult(List.of(), total);
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<ChatMessageVo> items = new ArrayList<>(rows.size());
        for (ConversationMessage row : rows) {
            ChatMessageVo item = new ChatMessageVo();
            item.setId(row.getId());
            item.setMessageCode(row.getMessageCode());
            item.setParentMessageId(row.getParentMessageId());
            item.setRole(row.getRole());
            item.setMessageKind(row.getMessageKind());
            item.setContent(row.getContent());
            item.setSegmentsJson(row.getSegmentsJson());
            item.setContentFormat(row.getContentFormat());
            item.setStatus(normalizeText(row.getStatus()).toLowerCase());
            item.setErrorCode(row.getErrorCode());
            item.setErrorMessage(row.getErrorMessage());
            item.setParamsJson(buildHistoryParamsSummaryJson(row.getParamsJson()));
            item.setAttachmentsJson(row.getAttachmentsJson());
            item.setArtifactSummaryJson(row.getArtifactSummaryJson());
            // Performance optimization: Exclude token/usage/model fields for chat history API
            // These fields are available via separate Token Usage API for admin analytics
            // item.setPromptTokens(row.getPromptTokens());
            // item.setCompletionTokens(row.getCompletionTokens());
            // item.setTotalTokens(row.getTotalTokens());
            // item.setUsageAvailable(row.getUsageAvailable());
            // item.setLlmCallCount(row.getLlmCallCount());
            // item.setToolCallCount(row.getToolCallCount());
            // item.setModelId(row.getModelId());
            // item.setModelProvider(row.getModelProvider());
            // item.setModelName(row.getModelName());
            // item.setAdapterType(row.getAdapterType());
            // item.setUsageSummaryJson(row.getUsageSummaryJson());
            item.setCreatedAt(row.getCreatedAt() == null ? null : format.format(row.getCreatedAt()));
            item.setUpdatedAt(row.getUpdatedAt() == null ? null : format.format(row.getUpdatedAt()));
            item.setCompletedAt(row.getCompletedAt() == null ? null : format.format(row.getCompletedAt()));
            items.add(item);
        }
        return new MessagePageResult(items, total);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeleteResult deleteSession(
            Long userId, ConversationSessionType sessionType, String sessionId, Long scopeId) {
        ConversationSession session = findOwnedSession(userId, sessionType, sessionId, scopeId);
        if (session == null) {
            return new DeleteResult(true, true, 0, 0);
        }

        chatFileService.deletePersistedFiles(conversationMessageMapper.selectFileListsBySessionId(session.getId()));
        int affectedMessages = conversationMessageMapper.deleteBySessionId(session.getId());
        conversationEventService.deleteBySessionId(session.getId());
        conversationRunUsageService.deleteBySessionId(session.getId());
        int affectedSessions =
                conversationSessionMapper.deleteBySessionCode(userId, sessionType.name(), session.getSessionCode());
        return new DeleteResult(true, false, affectedSessions, affectedMessages);
    }

    @Transactional(rollbackFor = Exception.class)
    public RenameResult renameSession(
            Long userId, ConversationSessionType sessionType, String sessionId, Long scopeId, String name)
            throws TaskException {
        ConversationSession session = findOwnedSession(userId, sessionType, sessionId, scopeId);
        if (session == null) {
            throw new TaskException("会话不存在或无权修改", TaskException.Code.UNKNOWN);
        }

        String normalizedName = normalizeSessionName(name);
        conversationSessionMapper.updateSessionName(session.getId(), normalizedName);

        String scopeDisplayName = resolveScopeDisplayName(session.getSessionType(), session.getScopeId());
        return new RenameResult(
                session.getSessionCode(),
                normalizedName,
                decorateSessionTitle(normalizedName, session.getSessionType(), scopeDisplayName));
    }

    @Transactional(rollbackFor = Exception.class)
    public UpdateSessionChatModelResult updateSessionChatModel(
            Long userId, ConversationSessionType sessionType, String sessionId, Long scopeId, Long chatModelId)
            throws TaskException {
        if (sessionType != ConversationSessionType.GENERAL_CHAT
                && sessionType != ConversationSessionType.GENERAL_CHAT_V2) {
            throw new TaskException("当前仅支持通用聊天切换模型", TaskException.Code.UNKNOWN);
        }
        if (chatModelId == null || chatModelId <= 0) {
            throw new TaskException("模型不能为空", TaskException.Code.UNKNOWN);
        }
        ConversationSession session = findOwnedSession(userId, sessionType, sessionId, scopeId);
        if (session == null) {
            throw new TaskException("会话不存在或无权修改", TaskException.Code.UNKNOWN);
        }
        ModelLibraryService.ChatModelReferenceView chatModel =
                modelLibraryService.resolveChatModelReference(chatModelId);
        if (!Boolean.TRUE.equals(chatModel.available())) {
            throw new TaskException("当前模型不可用，请重新选择", TaskException.Code.UNKNOWN);
        }
        conversationSessionMapper.updateSessionChatModel(session.getId(), chatModelId);
        return new UpdateSessionChatModelResult(
                session.getSessionCode(), chatModelId, chatModel.displayName(), Boolean.TRUE);
    }

    public ConversationMessage findLatestAssistantMessage(
            Long userId, ConversationSessionType sessionType, String sessionId, Long scopeId) {
        ConversationSession session = findOwnedSession(userId, sessionType, sessionId, scopeId);
        if (session == null) {
            return null;
        }
        return conversationMessageMapper.selectLatestAssistantMessage(session.getId());
    }

    public ConversationMessage findPreviousAssistantMessage(ConversationContext context) {
        if (context == null || context.sessionId() == null || context.assistantMessageSequenceNo() == null) {
            return null;
        }
        return conversationMessageMapper.selectLatestAssistantMessageBeforeSequence(
                context.sessionId(), context.assistantMessageSequenceNo());
    }

    public ConversationMessage findLatestUserMessage(
            Long userId, ConversationSessionType sessionType, String sessionId, Long scopeId) {
        ConversationSession session = findOwnedSession(userId, sessionType, sessionId, scopeId);
        if (session == null) {
            return null;
        }
        return conversationMessageMapper.selectLatestUserMessage(session.getId());
    }

    public Long resolveSessionChatModelId(
            Long userId, ConversationSessionType sessionType, String sessionId, Long scopeId) {
        ConversationSession session = findOwnedSession(userId, sessionType, sessionId, scopeId);
        return session == null ? null : session.getChatModelId();
    }

    private ConversationSession findOwnedSession(
            Long userId, ConversationSessionType sessionType, String sessionId, Long scopeId) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(sessionId)) {
            return null;
        }
        return conversationSessionMapper.selectBySessionCode(userId, sessionType.name(), sessionId.trim(), scopeId);
    }

    private ConversationSession resolveOrCreateSession(
            Long userId,
            ConversationSessionType sessionType,
            String requestedSessionCode,
            Long scopeId,
            Long requestedChatModelId) {
        if (requestedChatModelId != null && requestedChatModelId > 0) {
            ModelLibraryService.ChatModelReferenceView requestedChatModel =
                    modelLibraryService.resolveChatModelReference(requestedChatModelId);
            if (!Boolean.TRUE.equals(requestedChatModel.available())) {
                throw new IllegalArgumentException("当前模型不可用，请重新选择");
            }
        }
        ConversationSession existing = null;
        if (StringUtils.hasText(requestedSessionCode)) {
            existing = conversationSessionMapper.selectBySessionCode(
                    userId, sessionType.name(), requestedSessionCode.trim(), scopeId);
        }
        if (existing != null) {
            if (requestedChatModelId != null
                    && requestedChatModelId > 0
                    && !requestedChatModelId.equals(existing.getChatModelId())) {
                conversationSessionMapper.updateSessionChatModel(existing.getId(), requestedChatModelId);
                existing.setChatModelId(requestedChatModelId);
            }
            return existing;
        }

        ConversationSession created = new ConversationSession();
        created.setSessionCode(nextResourceCode());
        created.setSessionType(sessionType.name());
        created.setScopeType(resolveScopeType(sessionType));
        created.setScopeId(scopeId);
        created.setChatModelId(requestedChatModelId);
        created.setName(DEFAULT_SESSION_NAME);
        created.setStatus(SESSION_STATUS_ACTIVE);
        created.setCreateUserId(userId);
        conversationSessionMapper.insert(created);
        return created;
    }

    private String nextResourceCode() {
        for (int i = 0; i < 5; i++) {
            String code = UlidGenerator.next();
            ConversationSession existing = conversationSessionMapper.selectBySessionCodeGlobal(code);
            if (existing == null) {
                return code;
            }
        }
        return UlidGenerator.next();
    }

    private String resolveScopeType(ConversationSessionType sessionType) {
        if (sessionType == null) {
            return null;
        }
        return switch (sessionType) {
            case SKILL_CHAT, PUBLISHED_SKILL_CHAT, CHANNEL_CHAT -> "SKILL";
            case EXPERT_SKILL_PACKAGE_CHAT -> "EXPERT_SKILL_PACKAGE";
            case DATASET_CHAT -> "DATASET";
            case KNOWLEDGE_QA -> "KNOWLEDGE_BASE";
            case SKILL_STUDIO_PROJECT_CHAT, SKILL_STUDIO_PROJECT_PREVIEW_CHAT -> "SKILL_STUDIO_PROJECT";
            default -> null;
        };
    }

    private String resolveSessionTypeLabel(String sessionType) {
        if (ConversationSessionType.GENERAL_CHAT.name().equals(sessionType)) {
            return "对话 极速模式";
        }
        if (ConversationSessionType.GENERAL_CHAT_V2.name().equals(sessionType)) {
            return "对话 任务模式";
        }
        if (ConversationSessionType.CHANNEL_CHAT.name().equals(sessionType)) {
            return "渠道对话";
        }
        if (ConversationSessionType.SKILL_CHAT.name().equals(sessionType)
                || ConversationSessionType.PUBLISHED_SKILL_CHAT.name().equals(sessionType)) {
            return "技能对话";
        }
        if (ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT.name().equals(sessionType)) {
            return "专家包对话";
        }
        if (ConversationSessionType.DATASET_CHAT.name().equals(sessionType)) {
            return "数据集对话";
        }
        if (ConversationSessionType.KNOWLEDGE_QA.name().equals(sessionType)) {
            return "知识库问答";
        }
        if (ConversationSessionType.SKILL_STUDIO_PROJECT_CHAT.name().equals(sessionType)) {
            return "技能工坊";
        }
        if (ConversationSessionType.SKILL_STUDIO_PROJECT_PREVIEW_CHAT.name().equals(sessionType)) {
            return "技能工坊试运行";
        }
        return "对话";
    }

    private String resolveSessionSourceType(String sessionType) {
        if (ConversationSessionType.CHANNEL_CHAT.name().equals(sessionType)) {
            return "CHANNEL";
        }
        if (ConversationSessionType.SKILL_CHAT.name().equals(sessionType)
                || ConversationSessionType.PUBLISHED_SKILL_CHAT.name().equals(sessionType)) {
            return "SKILL";
        }
        if (ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT.name().equals(sessionType)) {
            return "EXPERT_SKILL_PACKAGE";
        }
        if (ConversationSessionType.DATASET_CHAT.name().equals(sessionType)) {
            return "DATASET";
        }
        return "CHAT";
    }

    private String resolveSessionSourceLabel(ConversationSession session, String scopeDisplayName) {
        if (ConversationSessionType.CHANNEL_CHAT.name().equals(session.getSessionType())) {
            return resolveChannelSourceLabel(session);
        }
        if (StringUtils.hasText(scopeDisplayName)) {
            return scopeDisplayName.trim();
        }
        return "";
    }

    private String resolveSessionSourceLabel(String sessionType, String scopeDisplayName) {
        if (StringUtils.hasText(scopeDisplayName)
                && (ConversationSessionType.SKILL_CHAT.name().equals(sessionType)
                        || ConversationSessionType.PUBLISHED_SKILL_CHAT.name().equals(sessionType)
                        || ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT.name().equals(sessionType)
                        || ConversationSessionType.CHANNEL_CHAT.name().equals(sessionType)
                        || ConversationSessionType.DATASET_CHAT.name().equals(sessionType)
                        || ConversationSessionType.SKILL_STUDIO_PROJECT_CHAT.name().equals(sessionType)
                        || ConversationSessionType.SKILL_STUDIO_PROJECT_PREVIEW_CHAT.name().equals(sessionType))) {
            return scopeDisplayName.trim();
        }
        return "";
    }

    private String resolveChannelSourceLabel(ConversationSession session) {
        ChannelSessionBinding binding = resolveChannelBinding(session);
        return resolveChannelTypeLabel(binding == null ? "" : binding.getChannelType());
    }

    private String resolveSessionChannelType(ConversationSession session) {
        if (session == null || !ConversationSessionType.CHANNEL_CHAT.name().equals(session.getSessionType())) {
            return "";
        }
        ChannelSessionBinding binding = resolveChannelBinding(session);
        return binding == null || !StringUtils.hasText(binding.getChannelType()) ? "" : binding.getChannelType().trim();
    }

    private ChannelSessionBinding resolveChannelBinding(ConversationSession session) {
        if (session == null || !StringUtils.hasText(session.getSessionCode())) {
            return null;
        }
        try {
            return channelSessionBindingMapper.selectByChatSessionCode(session.getSessionCode());
        } catch (Exception ex) {
            log.debug("解析渠道会话绑定失败：sessionCode={}, error={}", session.getSessionCode(), ex.getMessage());
            return null;
        }
    }

    private String resolveSessionSourceIcon(ConversationSession session) {
        if (session == null) {
            return "";
        }
        if (ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT.name().equals(session.getSessionType())) {
            AgentDetailDto detail = resolveAgentDetail(session.getScopeId());
            return detail == null ? "" : normalizeText(detail.getIcon());
        }
        if (isSkillVisualSession(session.getSessionType())) {
            return skillCatalogService.resolveSkillVisual(session.getScopeId()).icon();
        }
        return "";
    }

    private String resolveSessionSourceIconColor(ConversationSession session) {
        if (session == null) {
            return "";
        }
        if (isSkillVisualSession(session.getSessionType())) {
            return skillCatalogService.resolveSkillVisual(session.getScopeId()).iconColor();
        }
        return "";
    }

    private boolean isSkillVisualSession(String sessionType) {
        return ConversationSessionType.SKILL_CHAT.name().equals(sessionType)
                || ConversationSessionType.PUBLISHED_SKILL_CHAT.name().equals(sessionType)
                || ConversationSessionType.CHANNEL_CHAT.name().equals(sessionType);
    }

    private String resolveChannelTypeLabel(String channelType) {
        String normalized = normalizeText(channelType).toLowerCase();
        return switch (normalized) {
            case "wecom", "wechat_work", "work_wechat" -> "企微";
            case "dingtalk", "dingding" -> "钉钉";
            case "weixin", "wechat" -> "微信";
            case "webchat" -> "WebChat";
            default -> StringUtils.hasText(channelType) ? channelType.trim() : "渠道";
        };
    }

    private String resolveSessionTitleSummary(ConversationSession session, String scopeDisplayName) {
        String rawName =
                StringUtils.hasText(session.getName()) ? session.getName().trim() : "";
        if (StringUtils.hasText(rawName) && !DEFAULT_SESSION_NAME.equals(rawName)) {
            String editableName = resolveEditableSessionName(session, scopeDisplayName);
            if (StringUtils.hasText(editableName) && !DEFAULT_SESSION_NAME.equals(editableName)) {
                return shrinkForSummary(editableName, MAX_AUTO_SESSION_NAME_LENGTH);
            }
        }
        if (StringUtils.hasText(session.getLastMessage())) {
            return shrinkForSummary(session.getLastMessage(), MAX_AUTO_SESSION_NAME_LENGTH);
        }
        return DEFAULT_SESSION_NAME;
    }

    private String resolveSessionSubtitle(ConversationSession session, String scopeDisplayName) {
        String titleSummary = resolveSessionTitleSummary(session, scopeDisplayName);
        String sourceLabel = resolveSessionSourceLabel(session.getSessionType(), scopeDisplayName);
        if (StringUtils.hasText(sourceLabel) && !sourceLabel.equals(titleSummary)) {
            return sourceLabel + " · " + titleSummary;
        }
        return titleSummary;
    }

    private String buildUserMessageParamsJson(
            String messageType, String finalMessage, String questionType, String rawParamsJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", normalizeText(messageType));
        payload.put("finalMessage", normalizeText(finalMessage));
        payload.put("questionType", normalizeText(questionType));
        Object businessParams = parseJsonIfPossible(rawParamsJson);
        if (businessParams != null) {
            payload.put("businessParams", businessParams);
        } else if (StringUtils.hasText(rawParamsJson)) {
            payload.put("businessParamsText", rawParamsJson.trim());
        }
        return JSON.toJSONString(payload);
    }

    @SuppressWarnings("unchecked")
    private String buildHistoryParamsSummaryJson(String rawParamsJson) {
        Object parsed = parseJsonIfPossible(rawParamsJson);
        if (!(parsed instanceof Map<?, ?> source) || source.isEmpty()) {
            return null;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        copyIfPresent(source, summary, "messageType");
        copyIfPresent(source, summary, "answerMode");
        copyIfPresent(source, summary, "routeReason");
        copyIfPresent(source, summary, "fallbackReason");
        copyIfPresent(source, summary, "artifactRequired");
        copyIfPresent(source, summary, "selectedSkillId");
        copyIfPresent(source, summary, "selectedSkillName");

        Object approval = source.get("approval");
        if (approval instanceof Map<?, ?> approvalMap) {
            Map<String, Object> approvalSummary = new LinkedHashMap<>();
            copyIfPresent(approvalMap, approvalSummary, "approvalCode");
            copyIfPresent(approvalMap, approvalSummary, "approvalStatus");
            copyIfPresent(approvalMap, approvalSummary, "executionStatus");
            copyIfPresent(approvalMap, approvalSummary, "riskLevel");
            copyIfPresent(approvalMap, approvalSummary, "triggerReason");
            copyIfPresent(approvalMap, approvalSummary, "decisionComment");
            copyIfPresent(approvalMap, approvalSummary, "toolName");
            copyIfPresent(approvalMap, approvalSummary, "toolDisplayName");
            if (!approvalSummary.isEmpty()) {
                summary.put("approval", approvalSummary);
            }
        }

        Object runtimeV2Engine = source.get("runtimeV2Engine");
        Object graphState = source.get("graphState");
        if (runtimeV2Engine != null && graphState instanceof Map<?, ?> graphStateMap) {
            summary.put("runtimeV2Engine", runtimeV2Engine);
            Object graphPreview = source.get("graphPreview");
            summary.put("graphPreview", graphPreview == null ? Boolean.TRUE : graphPreview);
            Map<String, Object> stateSummary = new LinkedHashMap<>();
            copyIfPresent(graphStateMap, stateSummary, "phase");
            copyIfPresent(graphStateMap, stateSummary, "iterationCount");
            copyIfPresent(graphStateMap, stateSummary, "status");
            copyIfPresent(graphStateMap, stateSummary, "errorMessage");
            if (!stateSummary.isEmpty()) {
                summary.put("graphState", stateSummary);
            }
            Object phaseTrace = source.get("phaseTrace");
            if (phaseTrace instanceof List<?> phaseTraceList && !phaseTraceList.isEmpty()) {
                summary.put("phaseTrace", phaseTraceList.size() > 20
                        ? phaseTraceList.subList(Math.max(0, phaseTraceList.size() - 20), phaseTraceList.size())
                        : phaseTraceList);
            }
        }

        return summary.isEmpty() ? null : JSON.toJSONString(summary);
    }

    private void copyIfPresent(Map<?, ?> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private String buildUserMessageEventPayload(
            String messageType, String finalMessage, String questionType, String rawParamsJson, String fileListJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", normalizeText(messageType));
        payload.put("finalMessage", normalizeText(finalMessage));
        payload.put("questionType", normalizeText(questionType));
        Object businessParams = parseJsonIfPossible(rawParamsJson);
        if (businessParams != null) {
            payload.put("businessParams", businessParams);
        }
        Object attachments = parseJsonIfPossible(fileListJson);
        if (attachments != null) {
            payload.put("attachments", attachments);
        }
        return JSON.toJSONString(payload);
    }

    private Object parseJsonIfPossible(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSON.parse(json.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> extractFileIds(String fileListJson) {
        Object parsed = parseJsonIfPossible(fileListJson);
        if (!(parsed instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(item -> item.get("id"))
                .map(value -> value == null ? null : normalizeText(String.valueOf(value)))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String toLastMessage(String answer, String message) {
        String preferred = StringUtils.hasText(answer) ? answer : message;
        if (!StringUtils.hasText(preferred)) {
            return "";
        }
        return shrinkForSummary(preferred, MAX_LAST_MESSAGE_LENGTH);
    }

    private String summarizeSessionName(String message) {
        if (!StringUtils.hasText(message)) {
            return DEFAULT_SESSION_NAME;
        }
        String text = message.trim();
        return text.length() <= MAX_AUTO_SESSION_NAME_LENGTH ? text : text.substring(0, MAX_AUTO_SESSION_NAME_LENGTH);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeText(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String shrinkForSummary(String value, int maxLength) {
        String normalized = normalizeText(value).replace("\r", "").replace("\n", " ");
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "…";
    }

    private String toJson(
            Map<String, Object> baseValues,
            String optionalKey1,
            Object optionalValue1,
            String optionalKey2,
            Object optionalValue2) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (baseValues != null && !baseValues.isEmpty()) {
            payload.putAll(baseValues);
        }
        if (StringUtils.hasText(optionalKey1) && optionalValue1 != null) {
            payload.put(optionalKey1, optionalValue1);
        }
        if (StringUtils.hasText(optionalKey2) && optionalValue2 != null) {
            payload.put(optionalKey2, optionalValue2);
        }
        return JSON.toJSONString(payload);
    }

    private String resolveScopeDisplayName(String sessionType, Long scopeId) {
        if (scopeId == null || scopeId <= 0) {
            return "";
        }
        if (ConversationSessionType.SKILL_CHAT.name().equals(sessionType)) {
            return skillCatalogService.resolveSkillDisplayName(scopeId);
        }
        if (ConversationSessionType.PUBLISHED_SKILL_CHAT.name().equals(sessionType)) {
            return skillCatalogService.resolveSkillDisplayName(scopeId);
        }
        if (ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT.name().equals(sessionType)) {
            AgentDetailDto detail = resolveAgentDetail(scopeId);
            return detail == null ? "" : normalizeText(detail.getAgentName());
        }
        if (ConversationSessionType.CHANNEL_CHAT.name().equals(sessionType)) {
            String skillName = skillCatalogService.resolveSkillDisplayName(scopeId);
            if (StringUtils.hasText(skillName)) {
                return skillName;
            }
            try {
                return integrationDatasetService.getDataset(scopeId).name();
            } catch (Exception ignored) {
                return "";
            }
        }
        if (ConversationSessionType.DATASET_CHAT.name().equals(sessionType)) {
            try {
                return integrationDatasetService.getDataset(scopeId).name();
            } catch (Exception ignored) {
                return "";
            }
        }
        if (ConversationSessionType.SKILL_STUDIO_PROJECT_CHAT.name().equals(sessionType)
                || ConversationSessionType.SKILL_STUDIO_PROJECT_PREVIEW_CHAT
                        .name()
                        .equals(sessionType)) {
            return "";
        }
        return "";
    }

    private AgentDetailDto resolveAgentDetail(Long agentId) {
        if (agentId == null || agentId <= 0) {
            return null;
        }
        try {
            return agentTemplateService.getAgentDetail(agentId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveSessionTitle(ConversationSession session, String scopeDisplayName) {
        String baseTitle =
                StringUtils.hasText(session.getName()) ? session.getName().trim() : DEFAULT_SESSION_NAME;
        return decorateSessionTitle(baseTitle, session.getSessionType(), scopeDisplayName);
    }

    private String resolveEditableSessionName(ConversationSession session, String scopeDisplayName) {
        String rawName =
                StringUtils.hasText(session.getName()) ? session.getName().trim() : DEFAULT_SESSION_NAME;
        if ((!ConversationSessionType.SKILL_CHAT.name().equals(session.getSessionType())
                        && !ConversationSessionType.PUBLISHED_SKILL_CHAT.name().equals(session.getSessionType())
                        && !ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT
                                .name()
                                .equals(session.getSessionType())
                        && !ConversationSessionType.CHANNEL_CHAT.name().equals(session.getSessionType())
                        && !ConversationSessionType.DATASET_CHAT.name().equals(session.getSessionType())
                        && !ConversationSessionType.SKILL_STUDIO_PROJECT_CHAT
                                .name()
                                .equals(session.getSessionType())
                        && !ConversationSessionType.SKILL_STUDIO_PROJECT_PREVIEW_CHAT
                                .name()
                                .equals(session.getSessionType()))
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
                        && !ConversationSessionType.PUBLISHED_SKILL_CHAT.name().equals(sessionType)
                        && !ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT.name().equals(sessionType)
                        && !ConversationSessionType.CHANNEL_CHAT.name().equals(sessionType)
                        && !ConversationSessionType.DATASET_CHAT.name().equals(sessionType)
                        && !ConversationSessionType.SKILL_STUDIO_PROJECT_CHAT
                                .name()
                                .equals(sessionType)
                        && !ConversationSessionType.SKILL_STUDIO_PROJECT_PREVIEW_CHAT
                                .name()
                                .equals(sessionType))
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
            Long userMessageId,
            Long assistantMessageId,
            Integer userMessageSequenceNo,
            Integer assistantMessageSequenceNo,
            Long createUserId,
            String scopeDisplayName,
            String message,
            boolean usingDefaultName) {}

    public record MessagePageResult(List<ChatMessageVo> items, int total) {}

    public record SessionPageResult(List<ChatSessionVo> items, int total) {}

    public record DeleteResult(boolean success, boolean alreadyDeleted, int affectedSessions, int affectedMessages) {}

    public record RenameResult(String sessionId, String name, String title) {}

    public record UpdateSessionChatModelResult(
            String sessionId, Long chatModelId, String chatModelDisplayName, Boolean chatModelAvailable) {}

    public Map<String, Object> buildMetaPayload(ConversationContext context) {
        return Map.of(
                "sessionId", context.sessionCode(),
                "messageId", context.assistantMessageId() == null ? "" : context.assistantMessageId(),
                "requestMessageId", context.userMessageId() == null ? "" : context.userMessageId(),
                "sessionType", context.sessionType(),
                "scopeId", context.scopeId() == null ? "" : context.scopeId(),
                "scopeDisplayName", context.scopeDisplayName() == null ? "" : context.scopeDisplayName());
    }
}
