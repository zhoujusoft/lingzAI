package lingzhou.agent.backend.business.chat.domain.enums;

public enum ConversationSessionType {
    GENERAL_CHAT,
    GENERAL_CHAT_V2,
    CHANNEL_CHAT,
    SKILL_CHAT,
    PUBLISHED_SKILL_CHAT,
    EXPERT_SKILL_PACKAGE_CHAT,
    KNOWLEDGE_QA,
    DATASET_CHAT,
    SKILL_STUDIO_PROJECT_CHAT,
    SKILL_STUDIO_PROJECT_PREVIEW_CHAT;

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
        if ("SKILL_CHAT_APP".equals(normalized)) {
            return PUBLISHED_SKILL_CHAT;
        }
        throw new IllegalArgumentException("Unsupported sessionType: " + value);
    }
}
