package lingzhou.agent.backend.capability.agentruntime.personal;

public enum PersonalAgentMode {
    CHAT_ONLY,
    CONTENT_ASSIST,
    EXECUTION_TASK;

    public static PersonalAgentMode fromPreparedMode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return CHAT_ONLY;
        }
        String normalized = value.trim().toUpperCase();
        for (PersonalAgentMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return CHAT_ONLY;
    }
}
