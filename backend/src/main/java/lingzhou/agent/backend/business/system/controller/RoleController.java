package lingzhou.agent.backend.business.system.controller;

import java.util.LinkedHashSet;
import java.util.List;
import lingzhou.agent.backend.business.BaseController;
import lingzhou.agent.backend.business.system.model.AgentSimpleDto;
import lingzhou.agent.backend.business.system.model.BatchBindRoleUsersInput;
import lingzhou.agent.backend.business.system.model.CreateRoleInput;
import lingzhou.agent.backend.business.system.model.RoleDetailDto;
import lingzhou.agent.backend.business.system.model.RolePageInput;
import lingzhou.agent.backend.business.system.model.RolePageResult;
import lingzhou.agent.backend.business.system.model.RoleResourcePermissionDto;
import lingzhou.agent.backend.business.system.model.UpdateRoleInput;
import lingzhou.agent.backend.business.system.service.AgentTemplateService;
import lingzhou.agent.backend.business.system.service.RoleResourcePermissionService;
import lingzhou.agent.backend.business.system.service.RoleService;
import lingzhou.agent.backend.business.system.service.UserAgentConfigService;
import lingzhou.agent.backend.common.api.ApiResponse;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/roles")
public class RoleController extends BaseController {

    private final RoleService roleService;
    private final AgentTemplateService agentTemplateService;
    private final UserAgentConfigService userAgentConfigService;
    private final RoleResourcePermissionService roleResourcePermissionService;

    public RoleController(
            RoleService roleService,
            AgentTemplateService agentTemplateService,
            UserAgentConfigService userAgentConfigService,
            RoleResourcePermissionService roleResourcePermissionService) {
        this.roleService = roleService;
        this.agentTemplateService = agentTemplateService;
        this.userAgentConfigService = userAgentConfigService;
        this.roleResourcePermissionService = roleResourcePermissionService;
    }

    @GetMapping
    public RolePageResult listRoles(RolePageInput input) {
        return roleService.listRoles(input);
    }

    @GetMapping("/{id}")
    public RoleDetailDto getRoleDetail(@PathVariable("id") Long id) {
        return roleService.getRoleDetail(id);
    }

    @PostMapping
    public ApiResponse<Void> createRole(@RequestBody CreateRoleInput input) {
        String error = roleService.createRole(input);
        if (StringUtils.isNotBlank(error)) {
            return ApiResponse.fail(400001, error);
        }
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updateRole(@PathVariable("id") Long id, @RequestBody UpdateRoleInput input) {
        if (input != null) {
            input.setId(id);
        }
        String error = roleService.updateRole(input);
        if (StringUtils.isNotBlank(error)) {
            return ApiResponse.fail(400001, error);
        }
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable("id") Long id) {
        String error = roleService.deleteRole(id);
        if (StringUtils.isNotBlank(error)) {
            return ApiResponse.fail(400001, error);
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/users/batch-bind")
    public ApiResponse<Void> batchBindUsers(
            @PathVariable("id") Long id, @RequestBody(required = false) BatchBindRoleUsersInput input) {
        String error = roleService.batchBindUsers(id, input);
        if (StringUtils.isNotBlank(error)) {
            return ApiResponse.fail(400001, error);
        }
        if (input != null && input.getUserIds() != null) {
            LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
            for (Long userId : input.getUserIds()) {
                if (userId != null && userId > 0L) {
                    uniqueIds.add(userId);
                }
            }
            for (Long userId : uniqueIds) {
                userAgentConfigService.syncUserConfig(userId, id);
            }
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/agents/enabled")
    public List<AgentSimpleDto> listEnabledAgents() {
        return agentTemplateService.listEnabledAgents();
    }

    @GetMapping("/{id}/resources")
    public RoleResourcePermissionDto getRoleResources(@PathVariable("id") Long id) {
        return roleResourcePermissionService.getRoleResources(id);
    }

    @PutMapping("/{id}/resources")
    public ApiResponse<Void> updateRoleResources(
            @PathVariable("id") Long id,
            @RequestBody RoleResourcePermissionDto dto) {
        String userIdStr = getUserId();
        Long createdBy = null;
        if (StringUtils.isNotBlank(userIdStr)) {
            try {
                createdBy = Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        roleResourcePermissionService.updateRoleResources(id, dto, createdBy);
        return ApiResponse.success(null);
    }
}
