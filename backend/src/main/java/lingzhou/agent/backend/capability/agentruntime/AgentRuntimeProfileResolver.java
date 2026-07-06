package lingzhou.agent.backend.capability.agentruntime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class AgentRuntimeProfileResolver {

    private static final String EXECUTION_MODE_HINT_DIRECT = "DIRECT";
    private static final String EXECUTION_MODE_HINT_TOOL = "TOOL";

    public AgentRuntimeProfileResolution resolve(ChatRuntimePreparedRequest prepared) {
        AgentRuntimeProfile profile = resolveProfile(prepared);
        AgentRuntimePipeline pipeline = resolvePipeline(prepared, profile);
        String executionModeHint = resolveExecutionModeHint(prepared);
        Boolean directFastMatched = resolveDirectFastMatched(prepared);
        log.debug(
                "[运行时画像] 信号判定：会话ID={}, runtimeSkillName={}, 主信号.executionModeHint={}, directFastMatched={}, toolAwareSignal={}",
                prepared == null ? null : prepared.sessionId(),
                prepared == null ? null : prepared.runtimeSkillName(),
                executionModeHint,
                directFastMatched,
                hasToolAwareRuntimeSignal(prepared));
        log.debug(
                "[运行时画像] 会话ID={}, 会话类型={}, 个人Agent={}, 运行时技能={}, executionModeHint={}, directFastMatched={}, 画像={}, 管线={}, 工具数={}",
                prepared == null ? null : prepared.sessionId(),
                prepared == null ? null : prepared.sessionType(),
                prepared != null && prepared.personalAgent(),
                prepared == null ? null : prepared.runtimeSkillName(),
                executionModeHint,
                directFastMatched,
                profile,
                pipeline,
                prepared == null || prepared.toolCallbacks() == null
                        ? 0
                        : prepared.toolCallbacks().size());
        return new AgentRuntimeProfileResolution(profile, pipeline);
    }

    private AgentRuntimeProfile resolveProfile(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || prepared.sessionType() == null) {
            return AgentRuntimeProfile.GENERAL_CHAT;
        }
        if (prepared.personalAgent()) {
            return AgentRuntimeProfile.PERSONAL_ASSISTANT;
        }
        return switch (prepared.sessionType()) {
            case DATASET_CHAT -> AgentRuntimeProfile.DATASET_CHAT;
            case SKILL_STUDIO_PROJECT_CHAT, SKILL_STUDIO_PROJECT_PREVIEW_CHAT -> AgentRuntimeProfile.SKILL_STUDIO;
            case SKILL_CHAT, PUBLISHED_SKILL_CHAT -> AgentRuntimeProfile.SKILL_CHAT;
            default -> {
                if (hasToolAwareRuntimeSignal(prepared)) {
                    yield AgentRuntimeProfile.SKILL_CHAT;
                }
                yield AgentRuntimeProfile.GENERAL_CHAT;
            }
        };
    }

    private AgentRuntimePipeline resolvePipeline(ChatRuntimePreparedRequest prepared, AgentRuntimeProfile profile) {
        if (prepared == null) {
            return AgentRuntimePipeline.GENERAL;
        }
        if (profile == AgentRuntimeProfile.PERSONAL_ASSISTANT) {
            return AgentRuntimePipeline.TOOL_AWARE;
        }
        if (profile == AgentRuntimeProfile.DATASET_CHAT
                || profile == AgentRuntimeProfile.SKILL_CHAT
                || profile == AgentRuntimeProfile.SKILL_STUDIO
                || isSkillLikeSessionType(prepared.sessionType())
                || hasToolAwareRuntimeSignal(prepared)) {
            return AgentRuntimePipeline.TOOL_AWARE;
        }
        return AgentRuntimePipeline.GENERAL;
    }

    private boolean hasToolAwareRuntimeSignal(ChatRuntimePreparedRequest prepared) {
        if (prepared == null) {
            return false;
        }
        String executionModeHint = resolveExecutionModeHint(prepared);
        if (EXECUTION_MODE_HINT_TOOL.equalsIgnoreCase(executionModeHint)) {
            return true;
        }
        if (EXECUTION_MODE_HINT_DIRECT.equalsIgnoreCase(executionModeHint)) {
            return false;
        }
        if (StringUtils.hasText(prepared.runtimeSkillName())) {
            return true;
        }
        return false;
    }

    private boolean isSkillLikeSessionType(ConversationSessionType sessionType) {
        return sessionType == ConversationSessionType.SKILL_CHAT
                || sessionType == ConversationSessionType.PUBLISHED_SKILL_CHAT
                || sessionType == ConversationSessionType.SKILL_STUDIO_PROJECT_PREVIEW_CHAT;
    }

    private String resolveExecutionModeHint(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || !StringUtils.hasText(prepared.paramsJson())) {
            return "";
        }
        try {
            Map<String, Object> payload =
                    JSON.parseObject(prepared.paramsJson(), new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                return "";
            }
            return normalizeText(payload.get("executionModeHint"));
        } catch (Exception ignored) {
            return "";
        }
    }

    private Boolean resolveDirectFastMatched(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || !StringUtils.hasText(prepared.paramsJson())) {
            return null;
        }
        try {
            Map<String, Object> payload =
                    JSON.parseObject(prepared.paramsJson(), new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                return null;
            }
            Object value = payload.get("directFastMatched");
            if (value instanceof Boolean boolValue) {
                return boolValue;
            }
            if (value instanceof String textValue && StringUtils.hasText(textValue)) {
                return Boolean.parseBoolean(textValue.trim());
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : "";
    }
}
