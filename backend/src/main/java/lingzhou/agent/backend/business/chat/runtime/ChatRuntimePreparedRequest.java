package lingzhou.agent.backend.business.chat.runtime;

import java.util.List;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import org.springframework.ai.tool.ToolCallback;

public record ChatRuntimePreparedRequest(
        ConversationSessionType sessionType,
        LingzRuntimeScopeType scopeType,
        String sessionId,
        Long scopeId,
        String scopeDisplayName,
        String message,
        String userMessage,
        String messageType,
        String questionType,
        String paramsJson,
        String fileListJson,
        List<ToolCallback> toolCallbacks,
        String systemPrompt,
        String systemPromptAppend,
        String runtimeSkillName,
        List<RuntimeSkillDescriptor> availableSkills,
        List<RuntimeLoadedSkill> loadedSkills,
        Long chatModelId,
        boolean personalAgent,
        String personalAgentMode) {

    public ChatRuntimePreparedRequest withSessionId(String nextSessionId) {
        return new ChatRuntimePreparedRequest(
                sessionType,
                scopeType,
                nextSessionId,
                scopeId,
                scopeDisplayName,
                message,
                userMessage,
                messageType,
                questionType,
                paramsJson,
                fileListJson,
                toolCallbacks,
                systemPrompt,
                systemPromptAppend,
                runtimeSkillName,
                availableSkills,
                loadedSkills,
                chatModelId,
                personalAgent,
                personalAgentMode);
    }

    public ChatRuntimePreparedRequest withParamsJson(String nextParamsJson) {
        return new ChatRuntimePreparedRequest(
                sessionType,
                scopeType,
                sessionId,
                scopeId,
                scopeDisplayName,
                message,
                userMessage,
                messageType,
                questionType,
                nextParamsJson,
                fileListJson,
                toolCallbacks,
                systemPrompt,
                systemPromptAppend,
                runtimeSkillName,
                availableSkills,
                loadedSkills,
                chatModelId,
                personalAgent,
                personalAgentMode);
    }

    public ChatRuntimePreparedRequest withPersonalAgentMode(String nextPersonalAgentMode) {
        return new ChatRuntimePreparedRequest(
                sessionType,
                scopeType,
                sessionId,
                scopeId,
                scopeDisplayName,
                message,
                userMessage,
                messageType,
                questionType,
                paramsJson,
                fileListJson,
                toolCallbacks,
                systemPrompt,
                systemPromptAppend,
                runtimeSkillName,
                availableSkills,
                loadedSkills,
                chatModelId,
                personalAgent,
                nextPersonalAgentMode);
    }

    public ChatRuntimePreparedRequest withSkillState(String nextParamsJson, List<RuntimeLoadedSkill> nextLoadedSkills) {
        return new ChatRuntimePreparedRequest(
                sessionType,
                scopeType,
                sessionId,
                scopeId,
                scopeDisplayName,
                message,
                userMessage,
                messageType,
                questionType,
                nextParamsJson,
                fileListJson,
                toolCallbacks,
                systemPrompt,
                systemPromptAppend,
                runtimeSkillName,
                availableSkills,
                nextLoadedSkills == null ? List.of() : List.copyOf(nextLoadedSkills),
                chatModelId,
                personalAgent,
                personalAgentMode);
    }

    public ChatRuntimePreparedRequest withChatModelId(Long nextChatModelId) {
        return new ChatRuntimePreparedRequest(
                sessionType,
                scopeType,
                sessionId,
                scopeId,
                scopeDisplayName,
                message,
                userMessage,
                messageType,
                questionType,
                paramsJson,
                fileListJson,
                toolCallbacks,
                systemPrompt,
                systemPromptAppend,
                runtimeSkillName,
                availableSkills,
                loadedSkills,
                nextChatModelId,
                personalAgent,
                personalAgentMode);
    }
}
