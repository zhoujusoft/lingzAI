package lingzhou.agent.backend.business.channel.domain.enums;

public enum ChannelRouteType {
    GENERAL_CHAT,
    SKILL_CHAT,
    DATASET_CHAT;

    public static ChannelRouteType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return GENERAL_CHAT;
        }
        return ChannelRouteType.valueOf(value.trim().toUpperCase());
    }
}
