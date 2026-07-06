package lingzhou.agent.backend.business.system.model;

import java.util.Date;

/**
 * Agent 列表项 DTO
 */
public class AgentListItemDto {

    private Long id;
    private String agentCode;
    private String agentName;
    private String description;
    private String icon;
    private Integer enabled;
    private Integer roleCount; // 关联的角色数量
    private Integer skillCount; // 默认技能数量
    private Integer toolCount; // 默认工具数量
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
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
