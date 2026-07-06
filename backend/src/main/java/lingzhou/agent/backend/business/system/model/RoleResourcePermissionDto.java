package lingzhou.agent.backend.business.system.model;

import java.util.List;

/**
 * 角色资源权限DTO
 */
public class RoleResourcePermissionDto {

    private Long roleId;
    private List<Long> skillIds;
    private List<Long> toolIds;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public List<Long> getSkillIds() {
        return skillIds;
    }

    public void setSkillIds(List<Long> skillIds) {
        this.skillIds = skillIds;
    }

    public List<Long> getToolIds() {
        return toolIds;
    }

    public void setToolIds(List<Long> toolIds) {
        this.toolIds = toolIds;
    }
}
