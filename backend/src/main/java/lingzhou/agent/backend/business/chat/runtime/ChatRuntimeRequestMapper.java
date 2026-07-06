package lingzhou.agent.backend.business.chat.runtime;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.service.ChatConversationService;

public final class ChatRuntimeRequestMapper {

    private ChatRuntimeRequestMapper() {}

    public static LingzRuntimeRequest forGeneral(ChatConversationService.GeneralChatRequest request) {
        if (request == null) {
            return empty(LingzRuntimeScopeType.GENERAL);
        }
        return new LingzRuntimeRequest(
                request.sessionId(),
                request.message(),
                request.fileIds() == null ? List.of() : request.fileIds(),
                request.messageType(),
                request.eventPayload(),
                request.systemPromptAppend(),
                request.options(),
                LingzRuntimeScopeType.GENERAL,
                null,
                null,
                request.mentionedSkillId(),
                request.chatModelId());
    }

    public static LingzRuntimeRequest forSkill(
            ChatConversationService.SkillChatRequest request, LingzRuntimeScopeType scopeType) {
        if (request == null) {
            return empty(scopeType);
        }
        return new LingzRuntimeRequest(
                request.sessionId(),
                request.message(),
                request.fileIds() == null ? List.of() : request.fileIds(),
                request.messageType(),
                request.eventPayload(),
                request.systemPromptAppend(),
                request.options(),
                scopeType,
                request.skillId(),
                null,
                null,
                null);
    }

    public static LingzRuntimeRequest forExpertPackage(ChatConversationService.ExpertPackageChatRequest request) {
        if (request == null) {
            return empty(LingzRuntimeScopeType.EXPERT_SKILL_PACKAGE);
        }
        return new LingzRuntimeRequest(
                request.sessionId(),
                request.message(),
                request.fileIds() == null ? List.of() : request.fileIds(),
                request.messageType(),
                request.eventPayload(),
                request.systemPromptAppend(),
                request.options(),
                LingzRuntimeScopeType.EXPERT_SKILL_PACKAGE,
                request.packageId(),
                null,
                request.mentionedSkillId(),
                request.chatModelId());
    }

    public static LingzRuntimeRequest forDataset(ChatConversationService.DatasetChatRequest request) {
        if (request == null) {
            return empty(LingzRuntimeScopeType.DATASET);
        }
        return new LingzRuntimeRequest(
                request.sessionId(),
                request.message(),
                List.of(),
                request.messageType(),
                request.eventPayload(),
                request.systemPromptAppend(),
                request.options(),
                LingzRuntimeScopeType.DATASET,
                null,
                null,
                null,
                null);
    }

    public static LingzRuntimeRequest forSkillStudio(
            String sessionId,
            String message,
            String messageType,
            Map<String, Object> eventPayload,
            String systemPromptAppend,
            Map<String, Object> options,
            Long projectId,
            String runtimeSkillName) {
        return new LingzRuntimeRequest(
                sessionId,
                message,
                List.of(),
                messageType,
                eventPayload,
                systemPromptAppend,
                options,
                LingzRuntimeScopeType.SKILL_STUDIO_PROJECT,
                projectId,
                runtimeSkillName,
                null,
                null);
    }

    public static LingzRuntimeRequest forSkillStudioPreview(
            String sessionId,
            String message,
            List<String> fileIds,
            String messageType,
            Map<String, Object> eventPayload,
            String systemPromptAppend,
            Map<String, Object> options,
            Long projectId,
            String runtimeSkillName) {
        return new LingzRuntimeRequest(
                sessionId,
                message,
                fileIds == null ? List.of() : fileIds,
                messageType,
                eventPayload,
                systemPromptAppend,
                options,
                LingzRuntimeScopeType.SKILL_STUDIO_PROJECT_PREVIEW,
                projectId,
                runtimeSkillName,
                null,
                null);
    }

    private static LingzRuntimeRequest empty(LingzRuntimeScopeType scopeType) {
        return new LingzRuntimeRequest(
                null, "", List.of(), "normal", null, null, null, scopeType, null, null, null, null);
    }
}
