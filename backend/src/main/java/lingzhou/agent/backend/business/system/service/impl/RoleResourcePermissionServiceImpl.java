package lingzhou.agent.backend.business.system.service.impl;

import lingzhou.agent.backend.business.system.dao.RoleResourcePermissionMapper;
import lingzhou.agent.backend.business.system.model.RoleResourcePermission;
import lingzhou.agent.backend.business.system.model.RoleResourcePermissionDto;
import lingzhou.agent.backend.business.system.service.RoleResourcePermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoleResourcePermissionServiceImpl implements RoleResourcePermissionService {

    private final RoleResourcePermissionMapper roleResourcePermissionMapper;

    public RoleResourcePermissionServiceImpl(RoleResourcePermissionMapper roleResourcePermissionMapper) {
        this.roleResourcePermissionMapper = roleResourcePermissionMapper;
    }

    @Override
    public RoleResourcePermissionDto getRoleResources(Long roleId) {
        if (roleId == null) {
            return null;
        }

        RoleResourcePermissionDto dto = new RoleResourcePermissionDto();
        dto.setRoleId(roleId);

        // 查询技能权限
        List<Long> skillIds = roleResourcePermissionMapper.selectResourceIdsByRole(roleId, "SKILL");
        dto.setSkillIds(skillIds != null ? skillIds : new ArrayList<>());

        // 查询工具权限
        List<Long> toolIds = roleResourcePermissionMapper.selectResourceIdsByRole(roleId, "TOOL");
        dto.setToolIds(toolIds != null ? toolIds : new ArrayList<>());

        return dto;
    }

    @Override
    @Transactional
    public void updateRoleResources(Long roleId, RoleResourcePermissionDto dto, Long createdBy) {
        if (roleId == null || dto == null) {
            return;
        }

        // 1. 删除该角色的所有旧权限
        roleResourcePermissionMapper.deleteByRoleId(roleId);

        // 2. 批量插入新权限
        List<RoleResourcePermission> permissions = new ArrayList<>();

        // 插入技能权限
        List<Long> skillIds = dto.getSkillIds();
        if (skillIds != null && !skillIds.isEmpty()) {
            for (Long skillId : skillIds) {
                if (skillId != null) {
                    RoleResourcePermission permission = new RoleResourcePermission(roleId, "SKILL", skillId);
                    permission.setCreatedBy(createdBy);
                    permissions.add(permission);
                }
            }
        }

        // 插入工具权限
        List<Long> toolIds = dto.getToolIds();
        if (toolIds != null && !toolIds.isEmpty()) {
            for (Long toolId : toolIds) {
                if (toolId != null) {
                    RoleResourcePermission permission = new RoleResourcePermission(roleId, "TOOL", toolId);
                    permission.setCreatedBy(createdBy);
                    permissions.add(permission);
                }
            }
        }

        // 批量插入
        if (!permissions.isEmpty()) {
            roleResourcePermissionMapper.batchInsert(permissions);
        }
    }

    @Override
    public boolean hasPermission(Long roleId, String resourceType, Long resourceId) {
        if (roleId == null || resourceType == null || resourceId == null) {
            return false;
        }
        return roleResourcePermissionMapper.exists(roleId, resourceType, resourceId);
    }

    @Override
    public List<Long> getRoleSkillIds(Long roleId) {
        if (roleId == null) {
            return new ArrayList<>();
        }
        List<Long> skillIds = roleResourcePermissionMapper.selectResourceIdsByRole(roleId, "SKILL");
        return skillIds != null ? skillIds : new ArrayList<>();
    }

    @Override
    public List<Long> getRoleToolIds(Long roleId) {
        if (roleId == null) {
            return new ArrayList<>();
        }
        List<Long> toolIds = roleResourcePermissionMapper.selectResourceIdsByRole(roleId, "TOOL");
        return toolIds != null ? toolIds : new ArrayList<>();
    }
}
