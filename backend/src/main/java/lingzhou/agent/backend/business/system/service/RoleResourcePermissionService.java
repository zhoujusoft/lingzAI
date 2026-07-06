package lingzhou.agent.backend.business.system.service;

import lingzhou.agent.backend.business.system.model.RoleResourcePermissionDto;

import java.util.List;

public interface RoleResourcePermissionService {

    /**
     * 获取角色的资源权限
     */
    RoleResourcePermissionDto getRoleResources(Long roleId);

    /**
     * 更新角色的资源权限
     */
    void updateRoleResources(Long roleId, RoleResourcePermissionDto dto, Long createdBy);

    /**
     * 检查用户是否有资源权限
     */
    boolean hasPermission(Long roleId, String resourceType, Long resourceId);

    /**
     * 获取角色的技能ID列表
     */
    List<Long> getRoleSkillIds(Long roleId);

    /**
     * 获取角色的工具ID列表
     */
    List<Long> getRoleToolIds(Long roleId);
}
