package lingzhou.agent.backend.business.chat.domain.enums;

public enum ConversationSessionType {
    GENERAL_CHAT,
    SKILL_CHAT,
    KNOWLEDGE_QA,
    DATASET_CHAT;

    public static ConversationSessionType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return SKILL_CHAT;
        }
        String normalized = value.trim().toUpperCase();
        for (ConversationSessionType item : values()) {
            if (item.name().equals(normalized)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unsupported sessionType: " + value);
    }
}
