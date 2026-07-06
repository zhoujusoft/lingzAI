package lingzhou.agent.backend.capability.agentruntime.contract;

import org.springframework.util.StringUtils;

public record RuntimeSkillReadFactContract(String skillName, String displayName, String message, String toolCallId) {

    public RuntimeSkillReadFactContract {
        skillName = normalize(skillName);
        displayName = normalize(displayName);
        message = normalize(message);
        toolCallId = normalize(toolCallId);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
