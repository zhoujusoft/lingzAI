package lingzhou.agent.backend.business.system.model;

import java.util.List;

public class UpdateRoleInput {

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer enabled;
    private Long agentId;
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

    public List<String> getMenuPermissions() {
        return menuPermissions;
    }

    public void setMenuPermissions(List<String> menuPermissions) {
        this.menuPermissions = menuPermissions;
    }
}
