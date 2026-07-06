package lingzhou.agent.backend.business.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lingzhou.agent.backend.business.system.dao.SysRoleMapper;
import lingzhou.agent.backend.business.system.dao.SysRoleMenuPermissionMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.BatchBindRoleUsersInput;
import lingzhou.agent.backend.business.system.model.CreateRoleInput;
import lingzhou.agent.backend.business.system.model.RoleDetailDto;
import lingzhou.agent.backend.business.system.model.RoleListItemDto;
import lingzhou.agent.backend.business.system.model.RolePageInput;
import lingzhou.agent.backend.business.system.model.RolePageResult;
import lingzhou.agent.backend.business.system.model.SysRole;
import lingzhou.agent.backend.business.system.model.SysRoleMenuPermission;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.system.model.UpdateRoleInput;
import lingzhou.agent.backend.business.system.service.RoleService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleServiceImpl implements RoleService {

    private static final Set<String> ALLOWED_MENU_PERMISSION_KEYS = Set.of(
            "admin.knowledge.view",
            "admin.skillstudio.view",
            "admin.sandbox-test.view",
            "admin.skill-management.view",
            "admin.mcp-management.view",
            "admin.channel-management.view",
            "admin.model-library.view",
            "admin.token-usage.view",
            "admin.integration.data-sources.view",
            "admin.integration.datasets.view",
            "admin.api-library.view",
            "admin.tool-library.view",
            "admin.system.agents.view",
            "admin.system.roles.view",
            "admin.system.users.view",
            "admin.system.token-quota.view",
            "admin.system.configs.view",
            "admin.system.user-agent-config.view");

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuPermissionMapper sysRoleMenuPermissionMapper;
    private final SysUserMapper sysUserMapper;

    public RoleServiceImpl(
            SysRoleMapper sysRoleMapper,
            SysRoleMenuPermissionMapper sysRoleMenuPermissionMapper,
            SysUserMapper sysUserMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuPermissionMapper = sysRoleMenuPermissionMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public RolePageResult listRoles(RolePageInput input) {
        int page = input != null && input.getPage() != null ? input.getPage() : 1;
        int pageSize = input != null && input.getPageSize() != null ? input.getPageSize() : 10;
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));

        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(input != null ? input.getKeyword() : null)) {
            String keyword = "%" + input.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(SysRole::getRoleCode, keyword).or().like(SysRole::getRoleName, keyword));
        }
        wrapper.orderByDesc(SysRole::getUpdatedAt);

        Page<SysRole> pageRequest = new Page<>(safePage, safePageSize);
        IPage<SysRole> pageData = sysRoleMapper.selectPage(pageRequest, wrapper);

        List<RoleListItemDto> items =
                pageData.getRecords().stream().map(this::toListItemDto).collect(Collectors.toList());

        RolePageResult result = new RolePageResult();
        result.setItems(items);
        result.setTotal(pageData.getTotal());
        result.setPage(safePage);
        result.setPageSize(safePageSize);
        return result;
    }

    @Override
    public RoleDetailDto getRoleDetail(Long roleId) {
        if (roleId == null) {
            return null;
        }
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            return null;
        }

        RoleDetailDto dto = new RoleDetailDto();
        dto.setId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setDescription(role.getDescription());
        dto.setEnabled(role.getEnabled());
        dto.setCreatedAt(role.getCreatedAt());
        dto.setUpdatedAt(role.getUpdatedAt());
        dto.setMenuPermissions(listRoleMenuPermissions(roleId));

        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createRole(CreateRoleInput input) {
        if (input == null) {
            return "请求参数不能为空";
        }
        if (StringUtils.isBlank(input.getRoleCode())) {
            return "角色编码不能为空";
        }
        if (StringUtils.isBlank(input.getRoleName())) {
            return "角色名称不能为空";
        }

        // 校验编码唯一性
        LambdaQueryWrapper<SysRole> codeWrapper = new LambdaQueryWrapper<>();
        codeWrapper.eq(SysRole::getRoleCode, input.getRoleCode().trim());
        if (sysRoleMapper.selectCount(codeWrapper) > 0) {
            return "角色编码已存在";
        }

        List<String> normalizedPermissions = normalizeMenuPermissions(input.getMenuPermissions());
        String menuPermissionError = validateMenuPermissions(normalizedPermissions);
        if (menuPermissionError != null) {
            return menuPermissionError;
        }

        SysRole role = new SysRole();
        role.setRoleCode(input.getRoleCode().trim());
        role.setRoleName(input.getRoleName().trim());
        role.setDescription(StringUtils.trimToNull(input.getDescription()));
        role.setEnabled(input.getEnabled() != null ? input.getEnabled() : 1);

        sysRoleMapper.insert(role);
        replaceRoleMenuPermissions(role.getId(), normalizedPermissions);
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateRole(UpdateRoleInput input) {
        if (input == null || input.getId() == null) {
            return "角色ID不能为空";
        }

        SysRole existing = sysRoleMapper.selectById(input.getId());
        if (existing == null) {
            return "角色不存在";
        }

        // 校验编码唯一性（如果修改了编码）
        if (StringUtils.isNotBlank(input.getRoleCode())
                && !input.getRoleCode().trim().equals(existing.getRoleCode())) {
            LambdaQueryWrapper<SysRole> codeWrapper = new LambdaQueryWrapper<>();
            codeWrapper.eq(SysRole::getRoleCode, input.getRoleCode().trim()).ne(SysRole::getId, input.getId());
            if (sysRoleMapper.selectCount(codeWrapper) > 0) {
                return "角色编码已存在";
            }
        }

        List<String> normalizedPermissions = null;
        if (input.getMenuPermissions() != null) {
            normalizedPermissions = normalizeMenuPermissions(input.getMenuPermissions());
            String menuPermissionError = validateMenuPermissions(normalizedPermissions);
            if (menuPermissionError != null) {
                return menuPermissionError;
            }
        }

        if (StringUtils.isNotBlank(input.getRoleCode())) {
            existing.setRoleCode(input.getRoleCode().trim());
        }
        if (StringUtils.isNotBlank(input.getRoleName())) {
            existing.setRoleName(input.getRoleName().trim());
        }
        if (input.getDescription() != null) {
            existing.setDescription(StringUtils.trimToNull(input.getDescription()));
        }
        if (input.getEnabled() != null) {
            existing.setEnabled(input.getEnabled());
        }

        sysRoleMapper.updateById(existing);
        if (normalizedPermissions != null) {
            replaceRoleMenuPermissions(existing.getId(), normalizedPermissions);
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteRole(Long roleId) {
        if (roleId == null) {
            return "角色ID不能为空";
        }

        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            return "角色不存在";
        }

        // 检查是否有用户绑定此角色
        LambdaQueryWrapper<SysUserModel> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUserModel::getRoleId, roleId);
        if (sysUserMapper.selectCount(userWrapper) > 0) {
            return "该角色已被用户绑定，无法删除";
        }

        sysRoleMenuPermissionMapper.delete(
                new LambdaQueryWrapper<SysRoleMenuPermission>().eq(SysRoleMenuPermission::getRoleId, roleId));
        sysRoleMapper.deleteById(roleId);
        return null;
    }

    @Override
    public String getRoleNameById(Long roleId) {
        if (roleId == null) {
            return null;
        }
        SysRole role = sysRoleMapper.selectById(roleId);
        return role != null ? role.getRoleName() : null;
    }

    @Override
    public String checkRoleUsable(Long roleId) {
        if (roleId == null) {
            return null; // 不绑定角色视为可用
        }
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            return "绑定的角色不存在";
        }
        if (role.getEnabled() == null || role.getEnabled() != 1) {
            return "绑定的角色未启用";
        }
        return null; // 可用
    }

    @Override
    public List<String> listRoleMenuPermissions(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        List<SysRoleMenuPermission> permissions =
                sysRoleMenuPermissionMapper.selectList(new LambdaQueryWrapper<SysRoleMenuPermission>()
                        .eq(SysRoleMenuPermission::getRoleId, roleId)
                        .orderByAsc(SysRoleMenuPermission::getId));
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return permissions.stream()
                .map(SysRoleMenuPermission::getMenuKey)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String batchBindUsers(Long roleId, BatchBindRoleUsersInput input) {
        if (roleId == null) {
            return "角色ID不能为空";
        }
        String roleError = checkRoleUsable(roleId);
        if (roleError != null) {
            return roleError;
        }
        if (input == null || input.getUserIds() == null || input.getUserIds().isEmpty()) {
            return "请选择需要绑定的用户";
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long userId : input.getUserIds()) {
            if (userId != null && userId > 0L) {
                uniqueIds.add(userId);
            }
        }
        if (uniqueIds.isEmpty()) {
            return "请选择有效的用户";
        }

        List<Long> userIds = new ArrayList<>(uniqueIds);
        List<SysUserModel> users = sysUserMapper.selectBatchIds(userIds);
        if (users == null || users.isEmpty()) {
            return "用户不存在";
        }

        Map<Long, SysUserModel> userMap = users.stream().collect(Collectors.toMap(SysUserModel::getId, item -> item));
        Set<Long> missingIds = new HashSet<>(userIds);
        missingIds.removeAll(userMap.keySet());
        if (!missingIds.isEmpty()) {
            return "存在无效用户ID: "
                    + missingIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        }

        for (SysUserModel user : users) {
            if (!roleId.equals(user.getRoleId())) {
                user.setRoleId(roleId);
                int affectedRows = sysUserMapper.updateById(user);
                if (affectedRows <= 0) {
                    return "批量绑定用户失败";
                }
            }
        }
        return null;
    }

    private List<String> normalizeMenuPermissions(List<String> menuPermissions) {
        if (menuPermissions == null || menuPermissions.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String menuPermission : menuPermissions) {
            String value = StringUtils.trimToNull(menuPermission);
            if (value != null) {
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String validateMenuPermissions(List<String> menuPermissions) {
        for (String menuPermission : menuPermissions) {
            if (!ALLOWED_MENU_PERMISSION_KEYS.contains(menuPermission)) {
                return "存在不合法的菜单权限标识: " + menuPermission;
            }
        }
        return null;
    }

    private void replaceRoleMenuPermissions(Long roleId, List<String> menuPermissions) {
        if (roleId == null) {
            return;
        }
        sysRoleMenuPermissionMapper.delete(
                new LambdaQueryWrapper<SysRoleMenuPermission>().eq(SysRoleMenuPermission::getRoleId, roleId));
        if (menuPermissions == null || menuPermissions.isEmpty()) {
            return;
        }
        for (String menuPermission : menuPermissions) {
            SysRoleMenuPermission permission = new SysRoleMenuPermission();
            permission.setRoleId(roleId);
            permission.setMenuKey(menuPermission);
            sysRoleMenuPermissionMapper.insert(permission);
        }
    }

    private RoleListItemDto toListItemDto(SysRole role) {
        RoleListItemDto dto = new RoleListItemDto();
        dto.setId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setDescription(role.getDescription());
        dto.setEnabled(role.getEnabled());
        dto.setUpdatedAt(role.getUpdatedAt());

        return dto;
    }
}
