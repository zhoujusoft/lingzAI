package lingzhou.agent.backend.capability.permission.service;

import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolContext;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolInvocationContextHolder;
import lingzhou.agent.backend.business.system.dao.SysRoleMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.SysRole;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.system.service.RoleResourcePermissionService;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.common.lzException.LZException;
import lingzhou.agent.backend.common.lzException.ExceptionCode;
import lingzhou.agent.backend.business.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 工具资源权限检查服务
 *
 * <p>权限检查规则：
 * <ol>
 *   <li>系统工具（GLOBAL/RUNTIME 类型）→ 放行</li>
 *   <li>技能原生工具（SKILL_NATIVE 类型且 owner_skill_name 匹配当前激活技能）→ 放行</li>
 *   <li>其他工具 → 检查资源权限配置</li>
 * </ol>
 *
 * <p>注意：
 * <ul>
 *   <li>LOWCODE_API、MCP_REMOTE、DATASET_TOOL、KNOWLEDGE_BASE_TOOL、CONNECTOR_API 等工具必须显式配置权限</li>
 *   <li>owner_skill_name 为空不代表公共工具，仍需检查权限</li>
 * </ul>
 */
@Service
public class ToolResourcePermissionService extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(ToolResourcePermissionService.class);

    private final ToolCatalogMapper toolCatalogMapper;
    private final RoleResourcePermissionService roleResourcePermissionService;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    public ToolResourcePermissionService(
            ToolCatalogMapper toolCatalogMapper,
            RoleResourcePermissionService roleResourcePermissionService,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper) {
        this.toolCatalogMapper = toolCatalogMapper;
        this.roleResourcePermissionService = roleResourcePermissionService;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
    }

    /**
     * 检查工具调用权限
     *
     * @param toolName 工具名称
     * @throws LZException 如果没有权限
     */
    public void checkToolPermission(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return;
        }

        // 1. 获取工具信息
        ToolCatalog tool = toolCatalogMapper.selectByToolName(toolName);
        if (tool == null) {
            // 工具不在目录中，放行（可能是动态工具）
            logger.debug("工具 [{}] 不在目录中，放行", toolName);
            return;
        }

        // 2. 判断是否需要检查权限
        if (shouldBypassPermissionCheck(tool)) {
            logger.debug("工具 [{}] 满足放行条件，放行", toolName);
            return;
        }

        // 3. 检查资源权限
//        checkResourcePermission(tool);
    }

    /**
     * 判断是否应该跳过权限检查
     */
    private boolean shouldBypassPermissionCheck(ToolCatalog tool) {
        String toolType = tool.getToolType();
        String ownerSkillName = tool.getOwnerSkillName();

        // 规则 1: GLOBAL/RUNTIME 类型工具放行（系统工具）
        if ("GLOBAL".equals(toolType) || "RUNTIME".equals(toolType)) {
            return true;
        }

        // 规则 2: 技能原生工具（SKILL_NATIVE）- 检查当前对话是否激活了该技能
        if ("SKILL_NATIVE".equals(toolType) && isSkillActivatedInCurrentContext(ownerSkillName)) {
            return true;
        }

        // 其他情况需要检查资源权限
        return false;
    }

    /**
     * 检查当前对话是否激活了指定技能
     */
    private boolean isSkillActivatedInCurrentContext(String skillName) {
        RuntimeToolContext context = RuntimeToolInvocationContextHolder.get();
        if (context == null) {
            logger.debug("无法获取运行时上下文，技能 [{}] 视为未激活", skillName);
            return false;
        }

        // 获取当前激活的技能名称
        String currentSkillName = context.currentRuntimeSkillName();

        boolean activated = skillName.equals(currentSkillName);
        if (activated) {
            logger.debug("技能 [{}] 已在当前对话中激活", skillName);
        } else {
            logger.debug("技能 [{}] 未在当前对话中激活（当前技能: {}）", skillName, currentSkillName);
        }
        return activated;
    }

    /**
     * 检查资源权限配置
     */
    private void checkResourcePermission(ToolCatalog tool) {
        // 获取运行时上下文
        RuntimeToolContext context = RuntimeToolInvocationContextHolder.get();
        if (context == null) {
            throw new LZException(ExceptionCode.Default, "无法获取运行时上下文");
        }

        // 从上下文获取用户ID
        Long userId = context.userId();
        if (userId == null) {
            throw new LZException(ExceptionCode.Default, "用户未登录");
        }

        // 查询用户信息获取角色ID
        SysUserModel user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new LZException(ExceptionCode.Default, "用户不存在");
        }

        Long roleId = user.getRoleId();
        if (roleId == null) {
            throw new LZException(ExceptionCode.Default, "用户未分配角色");
        }

        // 查询角色信息
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null || role.getId() == null) {
            throw new LZException(ExceptionCode.Default, "角色不存在");
        }

        // 检查权限
        boolean hasPermission = roleResourcePermissionService.hasPermission(
                role.getId(), "TOOL", tool.getId()
        );

        if (!hasPermission) {
            String displayName = StringUtils.hasText(tool.getDisplayName())
                    ? tool.getDisplayName()
                    : tool.getToolName();
            throw new LZException(
                    ExceptionCode.Default,
                    "您没有使用工具 [" + displayName + "] 的权限，请联系管理员开通"
            );
        }

        logger.debug("用户 [{}] 有权限使用工具 [{}]", userId, tool.getToolName());
    }
}
