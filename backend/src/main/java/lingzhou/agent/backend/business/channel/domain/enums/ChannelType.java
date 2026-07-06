package lingzhou.agent.backend.business.channel.domain.enums;

public enum ChannelType {
    WEIXIN,
    WEBHOOK,
    DINGTALK,
    FEISHU,
    TELEGRAM,
    DISCORD,
    WECOM,
    QQ,
    SLACK,
    WEBCHAT;

    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return WEBHOOK.name().toLowerCase();
        }
        return value.trim().toLowerCase();
    }
}
