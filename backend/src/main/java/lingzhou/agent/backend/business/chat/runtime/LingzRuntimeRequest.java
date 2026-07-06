package lingzhou.agent.backend.business.chat.runtime;

import java.util.List;
import java.util.Map;

public record LingzRuntimeRequest(
        String sessionId,
        String message,
        List<String> fileIds,
        String messageType,
        Map<String, Object> eventPayload,
        String systemPromptAppend,
        Map<String, Object> options,
        LingzRuntimeScopeType scopeType,
        Long scopeId,
        String runtimeSkillName,
        Long mentionedSkillId,
        Long chatModelId) {

    public boolean isPersonalAgentRequest() {
        if (options == null || options.isEmpty()) {
            return false;
        }
        Object personalAgent = options.get("personalAgent");
        if (personalAgent instanceof Boolean boolValue) {
            return boolValue;
        }
        if (personalAgent instanceof String textValue) {
            return Boolean.parseBoolean(textValue.trim());
        }
        if (personalAgent instanceof Map<?, ?> mapValue) {
            Object enabled = mapValue.get("enabled");
            if (enabled instanceof Boolean enabledValue) {
                return enabledValue;
            }
            if (enabled instanceof String enabledText) {
                return Boolean.parseBoolean(enabledText.trim());
            }
        }
        return false;
    }

    public String resolvePersonalAgentMode() {
        if (options == null || options.isEmpty()) {
            return "";
        }
        Object personalAgent = options.get("personalAgent");
        if (personalAgent instanceof Map<?, ?> mapValue) {
            Object mode = mapValue.get("mode");
            if (mode != null) {
                return String.valueOf(mode).trim().toUpperCase();
            }
        }
        Object mode = options.get("personalAgentMode");
        return mode == null ? "" : String.valueOf(mode).trim().toUpperCase();
    }
}
