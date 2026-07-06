package lingzhou.agent.backend.business.system.model;

import java.util.Date;
import java.util.List;

/**
 * Agent 详情 DTO（用于管理页面）
 */
public class AgentDetailDto {

    private Long id;
    private String agentCode;
    private String agentName;
    private String description;
    private String openingMessage;
    private String icon;
    private String displayName;
    private String avatarObjectName;
    private String avatarUrl;
    private String soulTemplate;
    private String profileTemplate;
    private Integer enabled;
    private Integer roleCount;
    private Integer skillCount;
    private Integer toolCount;
    private List<SkillSimpleDto> skills;
    private List<ToolSimpleDto> tools;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOpeningMessage() {
        return openingMessage;
    }

    public void setOpeningMessage(String openingMessage) {
        this.openingMessage = openingMessage;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarObjectName() {
        return avatarObjectName;
    }

    public void setAvatarObjectName(String avatarObjectName) {
        this.avatarObjectName = avatarObjectName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getSoulTemplate() {
        return soulTemplate;
    }

    public void setSoulTemplate(String soulTemplate) {
        this.soulTemplate = soulTemplate;
    }

    public String getProfileTemplate() {
        return profileTemplate;
    }

    public void setProfileTemplate(String profileTemplate) {
        this.profileTemplate = profileTemplate;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Integer getRoleCount() {
        return roleCount;
    }

    public void setRoleCount(Integer roleCount) {
        this.roleCount = roleCount;
    }

    public Integer getSkillCount() {
        return skillCount;
    }

    public void setSkillCount(Integer skillCount) {
        this.skillCount = skillCount;
    }

    public Integer getToolCount() {
        return toolCount;
    }

    public void setToolCount(Integer toolCount) {
        this.toolCount = toolCount;
    }

    public List<SkillSimpleDto> getSkills() {
        return skills;
    }

    public void setSkills(List<SkillSimpleDto> skills) {
        this.skills = skills;
    }

    public List<ToolSimpleDto> getTools() {
        return tools;
    }

    public void setTools(List<ToolSimpleDto> tools) {
        this.tools = tools;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
