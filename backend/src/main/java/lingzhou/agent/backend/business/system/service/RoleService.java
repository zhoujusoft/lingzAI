package lingzhou.agent.backend.business.system.service;

import java.util.List;
import lingzhou.agent.backend.business.system.model.BatchBindRoleUsersInput;
import lingzhou.agent.backend.business.system.model.CreateRoleInput;
import lingzhou.agent.backend.business.system.model.RoleDetailDto;
import lingzhou.agent.backend.business.system.model.RolePageInput;
import lingzhou.agent.backend.business.system.model.RolePageResult;
import lingzhou.agent.backend.business.system.model.UpdateRoleInput;

public interface RoleService {

    /**
     * 分页查询角色列表
     */
    RolePageResult listRoles(RolePageInput input);

    /**
     * 获取角色详情
     */
    RoleDetailDto getRoleDetail(Long roleId);

    /**
     * 创建角色
     */
    String createRole(CreateRoleInput input);

    /**
     * 更新角色
     */
    String updateRole(UpdateRoleInput input);

    /**
     * 删除角色
     */
    String deleteRole(Long roleId);

    /**
     * 根据ID获取角色名称
     */
    String getRoleNameById(Long roleId);

    /**
     * 检查角色是否可用
     * 角色可用 = 角色启用 且 (Agent为空 或 Agent启用)
     * @param roleId 角色ID
     * @return null表示可用，否则返回错误信息
     */
    String checkRoleUsable(Long roleId);

    List<String> listRoleMenuPermissions(Long roleId);

    String batchBindUsers(Long roleId, BatchBindRoleUsersInput input);
}
