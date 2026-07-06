package lingzhou.agent.backend.business.chat.runtime;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lingzhou.agent.backend.capability.agentruntime.personal.PersonalAgentExecutionSnapshotService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChatRuntimePreparedRequestResolver {

    private final ConversationHistoryService conversationHistoryService;
    private final RuntimeSkillStateRecoveryService runtimeSkillStateRecoveryService;
    private final PersonalAgentExecutionSnapshotService personalAgentExecutionSnapshotService;

    public ChatRuntimePreparedRequestResolver(
            ConversationHistoryService conversationHistoryService,
            RuntimeSkillStateRecoveryService runtimeSkillStateRecoveryService,
            PersonalAgentExecutionSnapshotService personalAgentExecutionSnapshotService) {
        this.conversationHistoryService = conversationHistoryService;
        this.runtimeSkillStateRecoveryService = runtimeSkillStateRecoveryService;
        this.personalAgentExecutionSnapshotService = personalAgentExecutionSnapshotService;
    }

    public ChatRuntimePreparedRequest resolve(ChatRuntimePreparedRequest prepared, Long userId) {
        ChatRuntimePreparedRequest preparedWithExecutionState = restoreCodeExecutionState(prepared, userId);
        ChatRuntimePreparedRequest preparedWithLoadedSkills =
                runtimeSkillStateRecoveryService.restoreLoadedSkills(preparedWithExecutionState, userId);
        ChatRuntimePreparedRequest preparedWithChatModel =
                restoreConversationChatModel(preparedWithLoadedSkills, userId);
        return personalAgentExecutionSnapshotService.enrichPreparedRequest(preparedWithChatModel);
    }

    private ChatRuntimePreparedRequest restoreCodeExecutionState(ChatRuntimePreparedRequest prepared, Long userId) {
        if (prepared == null || !isRecoverableSession(prepared) || !StringUtils.hasText(prepared.sessionId())) {
            return prepared;
        }
        var latestAssistantMessage = conversationHistoryService.findLatestAssistantMessage(
                userId, prepared.sessionType(), prepared.sessionId(), prepared.scopeId());
        if (latestAssistantMessage == null
                || !readRootBoolean(latestAssistantMessage.getParamsJson(), "codeExecutionActive")) {
            return prepared;
        }
        String mergedParamsJson = mergeRootBooleanFlag(prepared.paramsJson(), "codeExecutionActive", true);
        if (java.util.Objects.equals(mergedParamsJson, prepared.paramsJson())) {
            return prepared;
        }
        return prepared.withParamsJson(mergedParamsJson);
    }

    private ChatRuntimePreparedRequest restoreConversationChatModel(ChatRuntimePreparedRequest prepared, Long userId) {
        if (prepared == null
                || prepared.chatModelId() != null
                || userId == null
                || userId <= 0
                || !StringUtils.hasText(prepared.sessionId())) {
            return prepared;
        }
        Long boundChatModelId = conversationHistoryService.resolveSessionChatModelId(
                userId, prepared.sessionType(), prepared.sessionId(), prepared.scopeId());
        if (boundChatModelId == null || boundChatModelId <= 0) {
            return prepared;
        }
        return prepared.withChatModelId(boundChatModelId);
    }

    private boolean readRootBoolean(String paramsJson, String key) {
        if (!StringUtils.hasText(paramsJson) || !StringUtils.hasText(key)) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = JSON.parseObject(paramsJson, Map.class);
            if (payload == null || payload.isEmpty()) {
                return false;
            }
            Object value = payload.get(key);
            return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String mergeRootBooleanFlag(String paramsJson, String key, boolean value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.hasText(paramsJson)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = JSON.parseObject(paramsJson, Map.class);
                if (parsed != null && !parsed.isEmpty()) {
                    payload.putAll(parsed);
                }
            } catch (Exception ignored) {
                // ignore and rebuild a minimal payload
            }
        }
        if (value) {
            payload.put(key, true);
        } else {
            payload.remove(key);
        }
        return JSON.toJSONString(payload);
    }

    private boolean isRecoverableSession(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || prepared.sessionType() == null) {
            return false;
        }
        return prepared.sessionType() == ConversationSessionType.GENERAL_CHAT
                || prepared.sessionType() == ConversationSessionType.GENERAL_CHAT_V2
                || prepared.sessionType() == ConversationSessionType.CHANNEL_CHAT;
    }
}
