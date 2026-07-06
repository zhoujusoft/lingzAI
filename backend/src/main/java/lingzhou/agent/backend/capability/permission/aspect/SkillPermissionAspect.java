package lingzhou.agent.backend.capability.permission.aspect;

import lingzhou.agent.backend.business.system.service.RoleResourcePermissionService;
import lingzhou.agent.backend.business.system.dao.SysRoleMapper;
import lingzhou.agent.backend.business.system.model.SysRole;
import lingzhou.agent.backend.business.skill.mapper.SkillCatalogMapper;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import lingzhou.agent.backend.common.lzException.LZException;
import lingzhou.agent.backend.common.lzException.ExceptionCode;
import lingzhou.agent.backend.business.BaseController;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 技能调用权限切面。
 *
 * <p>已废弃，不参与聊天运行时技能权限控制。当前运行时权限由请求准备阶段控制：
 * 系统提示词只暴露用户可用技能，ToolCallback 列表只注入当前用户可用工具。
 */
@Deprecated
@Aspect
@Component
public class SkillPermissionAspect extends BaseController {

    private final RoleResourcePermissionService roleResourcePermissionService;
    private final SysRoleMapper sysRoleMapper;
    private final SkillCatalogMapper skillCatalogMapper;

    public SkillPermissionAspect(
            RoleResourcePermissionService roleResourcePermissionService,
            SysRoleMapper sysRoleMapper,
            SkillCatalogMapper skillCatalogMapper) {
        this.roleResourcePermissionService = roleResourcePermissionService;
        this.sysRoleMapper = sysRoleMapper;
        this.skillCatalogMapper = skillCatalogMapper;
    }

    /**
     * 拦截技能调用，检查权限
     */
    @Deprecated
    @Before("@annotation(lingzhou.agent.backend.capability.permission.annotation.InvokeSkill)")
    public void checkSkillPermission(JoinPoint joinPoint) {
        // 获取用户ID
        String userIdStr = getUserId();
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new LZException(ExceptionCode.Default, "用户未登录");
        }
//
//        // 获取用户的角色ID
//        Long userId;
//        try {
//            userId = Long.parseLong(userIdStr);
//        } catch (NumberFormatException e) {
//            throw new LZException(ExceptionCode.Default, "用户ID格式错误");
//        }
//
//        // 查询用户的角色
//        SysRole role = sysRoleMapper.selectById(userId); // 假设用户表有roleId字段
//        if (role == null || role.getId() == null) {
//            throw new LZException(ExceptionCode.Default, "用户未分配角色");
//        }
//
//        // 获取技能名称（从注解或参数中提取）
//        String skillName = extractSkillName(joinPoint);
//        if (skillName == null || skillName.isEmpty()) {
//            return; // 如果无法提取技能名称，跳过权限检查
//        }
//
//        // 查询技能ID
//        SkillCatalog skill = skillCatalogMapper.selectByRuntimeSkillName(skillName);
//        if (skill == null || skill.getId() == null) {
//            throw new LZException(ExceptionCode.Default, "技能不存在: " + skillName);
//        }
//
//        // 检查权限
//        boolean hasPermission = roleResourcePermissionService.hasPermission(
//                role.getId(), "SKILL", skill.getId()
//        );
//
//        if (!hasPermission) {
//            throw new LZException(ExceptionCode.Default, "您没有使用该技能的权限，请联系管理员开通");
//        }
    }

    /**
     * 从切点提取技能名称
     */
    private String extractSkillName(JoinPoint joinPoint) {
        // TODO: 根据实际业务逻辑从注解或方法参数中提取技能名称
        // 这里需要根据实际的注解定义来实现
        return null;
    }
}
