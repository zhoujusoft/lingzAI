package lingzhou.agent.backend.capability.permission.aspect;

import lingzhou.agent.backend.business.system.service.RoleResourcePermissionService;
import lingzhou.agent.backend.business.system.dao.SysRoleMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.business.BaseController;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 工具调用权限切面
 *
 * <p>注意：此切面已废弃，不再参与运行时工具权限控制。
 *
 * <p>当前运行时权限由请求准备阶段控制：系统提示词只暴露用户可用技能，ToolCallback 列表只注入当前用户可用工具。
 * 工具执行阶段只执行已注入的回调，不再做额外资源鉴权。
 *
 * @see lingzhou.agent.backend.capability.permission.service.ToolResourcePermissionService
 * @see lingzhou.agent.backend.capability.agentruntime.execution.RuntimeToolExecutionService
 * @deprecated 运行时权限由提示词与 ToolCallback 注入控制
 */
@Deprecated
@Aspect
@Component
public class ToolPermissionAspect extends BaseController {

    private final RoleResourcePermissionService roleResourcePermissionService;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final ToolCatalogMapper toolCatalogMapper;

    public ToolPermissionAspect(
            RoleResourcePermissionService roleResourcePermissionService,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            ToolCatalogMapper toolCatalogMapper) {
        this.roleResourcePermissionService = roleResourcePermissionService;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.toolCatalogMapper = toolCatalogMapper;
    }

    /**
     * 拦截工具调用，检查权限
     *
     * @deprecated 此方法已不再使用，保留仅为兼容性考虑
     */
    @Deprecated
    @Before("@annotation(lingzhou.agent.backend.capability.permission.annotation.InvokeTool)")
    public void checkToolPermission(JoinPoint joinPoint) {
        // 已废弃，不再执行任何操作
    }

    /**
     * 从切点提取工具名称
     *
     * @deprecated 此方法已不再使用
     */
    @Deprecated
    private String extractToolName(JoinPoint joinPoint) {
        // TODO: 根据实际业务逻辑从注解或方法参数中提取工具名称
        return null;
    }
}
