package lingzhou.agent.backend.business.chat.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequestAssembler;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimeRequestMapper;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetService;
import lingzhou.agent.backend.business.skill.service.SkillCatalogService;
import lingzhou.agent.backend.business.skill.service.SkillPublishService;
import lingzhou.agent.backend.business.system.model.AgentDetailDto;
import lingzhou.agent.backend.business.system.service.AgentTemplateService;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2RequestHints;
import lingzhou.agent.backend.capability.agentruntime.v2.engine.RuntimeV2ExecutionGateway;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatConversationService {

    private final SkillCatalogService skillCatalogService;
    private final SkillPublishService skillPublishService;
    private final IntegrationDatasetService integrationDatasetService;
    private final AgentTemplateService agentTemplateService;
    private final ChatRuntimeExecutor chatRuntimeExecutor;
    private final ChatRuntimePreparedRequestAssembler chatRuntimePreparedRequestAssembler;
    private final RuntimeV2ExecutionGateway runtimeV2ExecutionGateway;

    public ChatConversationService(
            SkillCatalogService skillCatalogService,
            SkillPublishService skillPublishService,
            IntegrationDatasetService integrationDatasetService,
            AgentTemplateService agentTemplateService,
            ChatRuntimeExecutor chatRuntimeExecutor,
            ChatRuntimePreparedRequestAssembler chatRuntimePreparedRequestAssembler,
            RuntimeV2ExecutionGateway runtimeV2ExecutionGateway) {
        this.skillCatalogService = skillCatalogService;
        this.skillPublishService = skillPublishService;
        this.integrationDatasetService = integrationDatasetService;
        this.agentTemplateService = agentTemplateService;
        this.chatRuntimeExecutor = chatRuntimeExecutor;
        this.chatRuntimePreparedRequestAssembler = chatRuntimePreparedRequestAssembler;
        this.runtimeV2ExecutionGateway = runtimeV2ExecutionGateway;
    }

    public Flux<ServerSentEvent<String>> streamGeneral(GeneralChatRequest request, Long userId) {
        return streamGeneralBySessionType(request, userId, ConversationSessionType.GENERAL_CHAT);
    }

    public Flux<ServerSentEvent<String>> streamChannelGeneral(GeneralChatRequest request, Long userId) {
        return streamGeneralBySessionType(request, userId, ConversationSessionType.CHANNEL_CHAT);
    }

    private Flux<ServerSentEvent<String>> streamGeneralBySessionType(
            GeneralChatRequest request, Long userId, ConversationSessionType sessionType) {
        LingzRuntimeRequest normalized = ChatRuntimeRequestMapper.forGeneral(request);
        if (!chatRuntimePreparedRequestAssembler.hasRequestContent(normalized.message(), normalized.fileIds())) {
            return persistFrontFailure(
                    buildFallbackPrepared(sessionType, normalized), userId, "message or file is required");
        }
        ChatRuntimePreparedRequest prepared =
                chatRuntimePreparedRequestAssembler.buildGeneral(sessionType, normalized, userId);
        return streamPrepared(prepared, userId);
    }

    public Flux<ServerSentEvent<String>> streamSkill(SkillChatRequest request, Long userId) {
        if (request == null) {
            LingzRuntimeRequest normalized = ChatRuntimeRequestMapper.forSkill(null, LingzRuntimeScopeType.SKILL);
            return persistFrontFailure(
                    buildFallbackPrepared(ConversationSessionType.SKILL_CHAT, normalized),
                    userId,
                    "message or file is required");
        }
        try {
            SkillCatalogService.SkillChatContext context =
                    skillCatalogService.resolveSkillChatContext(request.skillId());
            return streamSkillByContext(request, userId, context, ConversationSessionType.SKILL_CHAT);
        } catch (TaskException ex) {
            LingzRuntimeRequest normalized = ChatRuntimeRequestMapper.forSkill(request, LingzRuntimeScopeType.SKILL);
            return persistFrontFailure(
                    buildFallbackPrepared(ConversationSessionType.SKILL_CHAT, normalized), userId, ex.getMessage());
        }
    }

    public Flux<ServerSentEvent<String>> streamChannelSkill(SkillChatRequest request, Long userId) {
        if (request == null) {
            LingzRuntimeRequest normalized = ChatRuntimeRequestMapper.forSkill(null, LingzRuntimeScopeType.SKILL);
            return persistFrontFailure(
                    buildFallbackPrepared(ConversationSessionType.CHANNEL_CHAT, normalized),
                    userId,
                    "message or file is required");
        }
        try {
            SkillCatalogService.SkillChatContext context =
                    skillCatalogService.resolveSkillChatContext(request.skillId());
            return streamSkillByContext(request, userId, context, ConversationSessionType.CHANNEL_CHAT);
        } catch (TaskException ex) {
            LingzRuntimeRequest normalized = ChatRuntimeRequestMapper.forSkill(request, LingzRuntimeScopeType.SKILL);
            return persistFrontFailure(
                    buildFallbackPrepared(ConversationSessionType.CHANNEL_CHAT, normalized), userId, ex.getMessage());
        }
    }

    public Flux<ServerSentEvent<String>> streamPublishedSkill(String appCode, SkillChatRequest request, Long userId) {
        try {
            SkillPublishService.PublishedSkillContext publishedContext =
                    skillPublishService.resolvePublishedSkillContext(appCode);
            SkillCatalogService.SkillChatContext context = skillCatalogService.resolveSkillChatContextForPublished(
                    publishedContext.skillId(), publishedContext.displayName(), publishedContext.description());
            return streamSkillByContext(request, userId, context, ConversationSessionType.PUBLISHED_SKILL_CHAT);
        } catch (TaskException ex) {
            LingzRuntimeRequest normalized =
                    ChatRuntimeRequestMapper.forSkill(request, LingzRuntimeScopeType.PUBLISHED_SKILL);
            return persistFrontFailure(
                    buildFallbackPrepared(ConversationSessionType.PUBLISHED_SKILL_CHAT, normalized),
                    userId,
                    ex.getMessage());
        }
    }

    private Flux<ServerSentEvent<String>> streamSkillByContext(
            SkillChatRequest request,
            Long userId,
            SkillCatalogService.SkillChatContext context,
            ConversationSessionType sessionType) {
        LingzRuntimeRequest normalized = ChatRuntimeRequestMapper.forSkill(
                request,
                sessionType == ConversationSessionType.PUBLISHED_SKILL_CHAT
                        ? LingzRuntimeScopeType.PUBLISHED_SKILL
                        : LingzRuntimeScopeType.SKILL);
        if (!chatRuntimePreparedRequestAssembler.hasSkillRequestContent(normalized)) {
            return persistFrontFailure(
                    buildFallbackPrepared(sessionType, normalized), userId, "message or file is required");
        }
        ChatRuntimePreparedRequest prepared =
                chatRuntimePreparedRequestAssembler.buildSkill(sessionType, normalized, context);
        return streamPrepared(prepared, userId);
    }

    public Flux<ServerSentEvent<String>> streamExpertPackage(ExpertPackageChatRequest request, Long userId) {
        LingzRuntimeRequest normalized = ChatRuntimeRequestMapper.forExpertPackage(request);
        if (request == null || request.packageId() == null) {
            return persistFrontFailure(
                    buildFallbackPrepared(ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT, normalized),
                    userId,
                    "expert package is required");
        }
        if (!chatRuntimePreparedRequestAssembler.hasRequestContent(normalized.message(), normalized.fileIds())) {
            return persistFrontFailure(
                    buildFallbackPrepared(ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT, normalized),
                    userId,
                    "message or file is required");
        }
        AgentDetailDto expertPackage = agentTemplateService.getEnabledAgentDetail(request.packageId());
        if (expertPackage == null) {
            return persistFrontFailure(
                    buildFallbackPrepared(ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT, normalized),
                    userId,
                    "专家技能包不存在或未启用");
        }
        ChatRuntimePreparedRequest prepared =
                chatRuntimePreparedRequestAssembler.buildExpertPackage(
                        ConversationSessionType.EXPERT_SKILL_PACKAGE_CHAT, normalized, expertPackage);
        return streamPrepared(prepared, userId);
    }

    public Flux<ServerSentEvent<String>> streamDataset(Long datasetId, DatasetChatRequest request, Long userId) {
        return streamDatasetBySessionType(datasetId, request, userId, ConversationSessionType.DATASET_CHAT);
    }

    public Flux<ServerSentEvent<String>> streamChannelDataset(Long datasetId, DatasetChatRequest request, Long userId) {
        return streamDatasetBySessionType(datasetId, request, userId, ConversationSessionType.CHANNEL_CHAT);
    }

    private Flux<ServerSentEvent<String>> streamDatasetBySessionType(
            Long datasetId, DatasetChatRequest request, Long userId, ConversationSessionType sessionType) {
        LingzRuntimeRequest normalized = ChatRuntimeRequestMapper.forDataset(request);
        if (!chatRuntimePreparedRequestAssembler.hasRequestContent(normalized.message(), null)) {
            return persistFrontFailure(
                    buildFallbackPrepared(sessionType, normalized), userId, "message or file is required");
        }
        try {
            IntegrationDatasetService.DatasetDetail dataset = integrationDatasetService.getDataset(datasetId, userId);
            ChatRuntimePreparedRequest prepared =
                    chatRuntimePreparedRequestAssembler.buildDataset(sessionType, normalized, dataset);
            return streamPrepared(prepared, userId);
        } catch (TaskException ex) {
            return persistFrontFailure(buildFallbackPrepared(sessionType, normalized), userId, ex.getMessage());
        }
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

    private Flux<ServerSentEvent<String>> streamPrepared(ChatRuntimePreparedRequest prepared, Long userId) {
        if (RuntimeV2RequestHints.readBooleanFlag(prepared == null ? null : prepared.paramsJson(), "artifactRequired")) {
            return runtimeV2ExecutionGateway.stream(prepared, userId);
        }
        return chatRuntimeExecutor.stream(prepared, userId);
    }

    private Flux<ServerSentEvent<String>> persistFrontFailure(
            ChatRuntimePreparedRequest prepared, Long userId, String errorMessage) {
        return chatRuntimeExecutor.persistPreRuntimeFailure(prepared, userId, errorMessage);
    }

    private ChatRuntimePreparedRequest buildFallbackPrepared(
            ConversationSessionType sessionType, LingzRuntimeRequest normalized) {
        String resolvedMessage = resolveFallbackMessage(normalized);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "fallback-error");
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
                com.alibaba.fastjson.JSON.toJSONString(params),
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

    private ServerSentEvent<String> errorEvent(String error) {
        return ChatSseEventBuilder.error(error);
    }

    public record GeneralChatRequest(
            String message,
            List<String> fileIds,
            String sessionId,
            String messageType,
            Map<String, Object> eventPayload,
            String systemPromptAppend,
            Map<String, Object> options,
            Long mentionedSkillId,
            Long chatModelId) {}

    public record SkillChatRequest(
            Long skillId,
            String message,
            List<String> fileIds,
            String sessionId,
            String messageType,
            Map<String, Object> eventPayload,
            String systemPromptAppend,
            Map<String, Object> options) {}

    public record ExpertPackageChatRequest(
            Long packageId,
            String message,
            List<String> fileIds,
            String sessionId,
            String messageType,
            Map<String, Object> eventPayload,
            String systemPromptAppend,
            Map<String, Object> options,
            Long mentionedSkillId,
            Long chatModelId) {}

    public record DatasetChatRequest(
            String message,
            String sessionId,
            String messageType,
            Map<String, Object> eventPayload,
            String systemPromptAppend,
            Map<String, Object> options) {}
}
