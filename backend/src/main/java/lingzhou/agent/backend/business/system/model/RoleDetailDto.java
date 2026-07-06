package lingzhou.agent.backend.business.system.model;

import java.util.Date;
import java.util.List;

public class RoleDetailDto {

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer enabled;
    private Long agentId;
    private Date createdAt;
    private Date updatedAt;

    // Agent信息
    private String agentCode;
    private String agentName;
    private String agentDescription;
    private String openingMessage;
    private String icon;
    private String soulTemplate;
    private String profileTemplate;
    private Integer agentEnabled; // Agent当前启用状态
    private Integer agentSkillCount;
    private List<SkillSimpleDto> agentSkills;
    private List<String> menuPermissions;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
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

    public String getAgentDescription() {
        return agentDescription;
    }

    public void setAgentDescription(String agentDescription) {
        this.agentDescription = agentDescription;
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

    public Integer getAgentEnabled() {
        return agentEnabled;
    }

    public void setAgentEnabled(Integer agentEnabled) {
        this.agentEnabled = agentEnabled;
    }

    public Integer getAgentSkillCount() {
        return agentSkillCount;
    }

    public void setAgentSkillCount(Integer agentSkillCount) {
        this.agentSkillCount = agentSkillCount;
    }

    public List<SkillSimpleDto> getAgentSkills() {
        return agentSkills;
    }

    public void setAgentSkills(List<SkillSimpleDto> agentSkills) {
        this.agentSkills = agentSkills;
    }

    public List<String> getMenuPermissions() {
        return menuPermissions;
    }

    public void setMenuPermissions(List<String> menuPermissions) {
        this.menuPermissions = menuPermissions;
    }
}
