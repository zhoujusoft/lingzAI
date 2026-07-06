package lingzhou.agent.backend.business.chat.runtime;

import java.util.List;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.service.ConversationHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class RuntimeSkillStateRecoveryService {

    private final ConversationHistoryService conversationHistoryService;
    private final RequestScopedSkillRuntimeService requestScopedSkillRuntimeService;

    public RuntimeSkillStateRecoveryService(
            ConversationHistoryService conversationHistoryService,
            RequestScopedSkillRuntimeService requestScopedSkillRuntimeService) {
        this.conversationHistoryService = conversationHistoryService;
        this.requestScopedSkillRuntimeService = requestScopedSkillRuntimeService;
    }

    public ChatRuntimePreparedRequest restoreLoadedSkills(ChatRuntimePreparedRequest prepared, Long userId) {
        if (prepared == null
                || prepared.availableSkills() == null
                || prepared.availableSkills().isEmpty()) {
            return prepared;
        }
        if (prepared.loadedSkills() != null && !prepared.loadedSkills().isEmpty()) {
            return mergeSkillState(prepared, prepared.loadedSkills(), prepared.paramsJson(), "request");
        }
        if (!isRecoverableSession(prepared)
                || userId == null
                || userId <= 0
                || !StringUtils.hasText(prepared.sessionId())) {
            return mergeSkillState(prepared, List.of(), prepared.paramsJson(), "empty");
        }
        var latestAssistantMessage = conversationHistoryService.findLatestAssistantMessage(
                userId, prepared.sessionType(), prepared.sessionId(), prepared.scopeId());
        String latestParamsJson = latestAssistantMessage == null ? null : latestAssistantMessage.getParamsJson();
        List<RuntimeLoadedSkill> loadedSkills = requestScopedSkillRuntimeService.resolveLoadedSkillsFromParams(
                latestParamsJson, prepared.availableSkills());
        return mergeSkillState(prepared, loadedSkills, latestParamsJson, "history");
    }

    private ChatRuntimePreparedRequest mergeSkillState(
            ChatRuntimePreparedRequest prepared,
            List<RuntimeLoadedSkill> loadedSkills,
            String currentSkillSourceParamsJson,
            String source) {
        String currentRuntimeSkillName = requestScopedSkillRuntimeService.resolveCurrentRuntimeSkillName(
                currentSkillSourceParamsJson, prepared.availableSkills(), prepared.runtimeSkillName());
        String mergedParamsJson = requestScopedSkillRuntimeService.mergeSkillStateParams(
                prepared.paramsJson(), prepared.availableSkills(), loadedSkills, currentRuntimeSkillName);
        log.debug(
                "[运行时画像] 技能状态已恢复：sessionId={}, source={}, currentRuntimeSkillName={}, loadedSkills={}",
                prepared.sessionId(),
                source,
                currentRuntimeSkillName,
                loadedSkills == null
                        ? List.of()
                        : loadedSkills.stream()
                                .map(RuntimeLoadedSkill::runtimeSkillName)
                                .toList());
        return prepared.withSkillState(mergedParamsJson, loadedSkills);
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
