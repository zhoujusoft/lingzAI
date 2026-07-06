package lingzhou.agent.backend.business.chat.domain.vo;

public class ChatSessionVo {

    private String id;
    private String name;
    private String title;
    private Boolean active;
    private String updatedAt;
    private String lastMessage;
    private String sessionType;
    private String sessionTypeLabel;
    private Long scopeId;
    private String scopeDisplayName;
    private String sourceType;
    private String sourceLabel;
    private String sourceIcon;
    private String sourceIconColor;
    private String channelType;
    private String titleSummary;
    private String subtitle;
    private Long chatModelId;
    private String chatModelDisplayName;
    private Boolean chatModelAvailable;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getSessionTypeLabel() {
        return sessionTypeLabel;
    }

    public void setSessionTypeLabel(String sessionTypeLabel) {
        this.sessionTypeLabel = sessionTypeLabel;
    }

    public Long getScopeId() {
        return scopeId;
    }

    public void setScopeId(Long scopeId) {
        this.scopeId = scopeId;
    }

    public String getScopeDisplayName() {
        return scopeDisplayName;
    }

    public void setScopeDisplayName(String scopeDisplayName) {
        this.scopeDisplayName = scopeDisplayName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public String getSourceIcon() {
        return sourceIcon;
    }

    public void setSourceIcon(String sourceIcon) {
        this.sourceIcon = sourceIcon;
    }

    public String getSourceIconColor() {
        return sourceIconColor;
    }

    public void setSourceIconColor(String sourceIconColor) {
        this.sourceIconColor = sourceIconColor;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getTitleSummary() {
        return titleSummary;
    }

    public void setTitleSummary(String titleSummary) {
        this.titleSummary = titleSummary;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public Long getChatModelId() {
        return chatModelId;
    }

    public void setChatModelId(Long chatModelId) {
        this.chatModelId = chatModelId;
    }

    public String getChatModelDisplayName() {
        return chatModelDisplayName;
    }

    public void setChatModelDisplayName(String chatModelDisplayName) {
        this.chatModelDisplayName = chatModelDisplayName;
    }

    public Boolean getChatModelAvailable() {
        return chatModelAvailable;
    }

    public void setChatModelAvailable(Boolean chatModelAvailable) {
        this.chatModelAvailable = chatModelAvailable;
    }
}
