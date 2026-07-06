package lingzhou.agent.backend.business.system.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lingzhou.agent.backend.business.system.model.RoleResourcePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleResourcePermissionMapper extends BaseMapper<RoleResourcePermission> {

    /**
     * 根据角色ID删除所有权限配置
     */
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量插入权限配置
     */
    void batchInsert(@Param("permissions") List<RoleResourcePermission> permissions);

    /**
     * 查询角色的资源ID列表
     */
    List<Long> selectResourceIdsByRole(@Param("roleId") Long roleId, @Param("resourceType") String resourceType);

    /**
     * 检查权限是否存在
     */
    boolean exists(@Param("roleId") Long roleId, @Param("resourceType") String resourceType, @Param("resourceId") Long resourceId);

    /**
     * 根据资源查询角色ID列表
     */
    List<Long> selectRoleIdsByResource(@Param("resourceType") String resourceType, @Param("resourceId") Long resourceId);
}
