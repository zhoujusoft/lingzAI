package lingzhou.agent.backend.business.system.controller;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.system.model.AgentSimpleDto;
import lingzhou.agent.backend.business.system.model.AgentDetailDto;
import lingzhou.agent.backend.business.system.model.UpdateUserAgentTemplateInput;
import lingzhou.agent.backend.business.system.model.UpdateUserAgentProfileInput;
import lingzhou.agent.backend.business.system.model.UpdateUserSkillPreferenceInput;
import lingzhou.agent.backend.business.system.model.UserAvatarUploadResult;
import lingzhou.agent.backend.business.system.model.UserAgentFile;
import lingzhou.agent.backend.business.system.model.UserSkillPreferenceDto;
import lingzhou.agent.backend.business.system.service.UserAgentConfigService;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/user-agent-config")
public class UserAgentConfigController {

    private final UserAgentConfigService userAgentConfigService;

    public UserAgentConfigController(UserAgentConfigService userAgentConfigService) {
        this.userAgentConfigService = userAgentConfigService;
    }

    /**
     * 获取用户档案文件列表
     */
    @GetMapping("/files/{userId}")
    public ApiResponse<List<UserAgentFile>> getUserFiles(@PathVariable("userId") Long userId) {
        userAgentConfigService.ensureUserConfig(userId, null);
        return ApiResponse.success(userAgentConfigService.getUserAgentFiles(userId));
    }

    @GetMapping("/template/{userId}")
    public ApiResponse<AgentDetailDto> getUserTemplate(@PathVariable("userId") Long userId) {
        userAgentConfigService.ensureUserConfig(userId, null);
        return ApiResponse.success(userAgentConfigService.getUserAgentTemplate(userId));
    }

    @GetMapping("/templates")
    public ApiResponse<List<AgentSimpleDto>> listAvailableTemplates() {
        return ApiResponse.success(userAgentConfigService.listAvailableAgents());
    }

    @PutMapping("/template/{userId}")
    public ApiResponse<String> updateUserTemplate(
            @PathVariable("userId") Long userId, @RequestBody UpdateUserAgentTemplateInput input) {
        userAgentConfigService.updateUserAgentTemplate(userId, input == null ? null : input.getAgentId());
        return ApiResponse.success("更新成功");
    }

    @PutMapping("/assistant/{userId}")
    public ApiResponse<String> updateUserAssistant(
            @PathVariable("userId") Long userId, @RequestBody UpdateUserAgentProfileInput input) {
        userAgentConfigService.updateUserAgentProfile(userId, input == null ? null : input.getAgentName());
        return ApiResponse.success("更新成功");
    }

    @PostMapping("/assistant/avatar/upload/{userId}")
    public ApiResponse<UserAvatarUploadResult> uploadUserAssistantAvatar(
            @PathVariable("userId") Long userId, @RequestPart("file") MultipartFile file) {
        try {
            return ApiResponse.success(userAgentConfigService.uploadUserAgentAvatar(userId, file));
        } catch (TaskException ex) {
            return ApiResponse.fail(400001, ex.getMessage());
        }
    }

    /**
     * 获取用户档案文件内容
     */
    @GetMapping("/file/{userId}/{filename}")
    public ApiResponse<UserAgentFile> getUserFile(
            @PathVariable("userId") Long userId, @PathVariable("filename") String filename) {
        userAgentConfigService.ensureUserConfig(userId, null);
        return ApiResponse.success(userAgentConfigService.getUserAgentFile(userId, filename));
    }

    /**
     * 更新用户档案文件内容
     */
    @PutMapping("/file/{userId}/{filename}")
    public ApiResponse<String> updateUserFile(
            @PathVariable("userId") Long userId,
            @PathVariable("filename") String filename,
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        userAgentConfigService.updateUserAgentFile(userId, filename, content);
        return ApiResponse.success("更新成功");
    }

    /**
     * 更新用户身份描述
     */
    @PutMapping("/profile/{userId}")
    public ApiResponse<String> updateUserProfile(
            @PathVariable("userId") Long userId, @RequestBody Map<String, String> body) {
        String profileContent = body.get("profileContent");
        userAgentConfigService.updateUserProfile(userId, profileContent);
        return ApiResponse.success("更新成功");
    }

    /**
     * 获取用户资源权限授予的技能ID列表
     */
    @GetMapping("/skills/{userId}")
    public ApiResponse<List<Long>> getUserSkills(@PathVariable("userId") Long userId) {
        userAgentConfigService.ensureUserConfig(userId, null);
        return ApiResponse.success(userAgentConfigService.getUserSkillIds(userId));
    }

    /**
     * 获取用户技能偏好：角色授权范围 + 个人启用子集
     */
    @GetMapping("/skills/preference/{userId}")
    public ApiResponse<UserSkillPreferenceDto> getUserSkillPreference(@PathVariable("userId") Long userId) {
        userAgentConfigService.ensureUserConfig(userId, null);
        return ApiResponse.success(userAgentConfigService.getUserSkillPreference(userId));
    }

    /**
     * 保存用户技能偏好；只能保存角色资源权限范围内的技能
     */
    @PutMapping("/skills/preference/{userId}")
    public ApiResponse<String> updateUserSkillPreference(
            @PathVariable("userId") Long userId, @RequestBody UpdateUserSkillPreferenceInput input) {
        userAgentConfigService.updateUserSkillPreference(
                userId, input == null ? List.of() : input.getEnabledSkillIds());
        return ApiResponse.success("更新成功");
    }

    /**
     * 添加用户启用技能；角色资源权限是允许范围
     */
    @PostMapping("/skill/{userId}/{skillId}")
    public ApiResponse<String> addUserSkill(
            @PathVariable("userId") Long userId, @PathVariable("skillId") Long skillId) {
        userAgentConfigService.addUserSkillBinding(userId, skillId);
        return ApiResponse.success("添加成功");
    }

    /**
     * 移除用户启用技能
     */
    @DeleteMapping("/skill/{userId}/{skillId}")
    public ApiResponse<String> removeUserSkill(
            @PathVariable("userId") Long userId, @PathVariable("skillId") Long skillId) {
        userAgentConfigService.removeUserSkillBinding(userId, skillId);
        return ApiResponse.success("移除成功");
    }

    /**
     * 手动初始化用户配置
     */
    @PostMapping("/init/{userId}")
    public ApiResponse<String> initUserConfig(
            @PathVariable("userId") Long userId, @RequestBody(required = false) Map<String, Long> body) {
        Long roleId = body != null ? body.get("roleId") : null;
        userAgentConfigService.ensureUserConfig(userId, roleId);
        return ApiResponse.success("初始化成功");
    }

    @PostMapping("/sync/{userId}")
    public ApiResponse<String> syncUserConfig(
            @PathVariable("userId") Long userId, @RequestBody(required = false) Map<String, Long> body) {
        Long roleId = body != null ? body.get("roleId") : null;
        userAgentConfigService.syncUserConfig(userId, roleId);
        return ApiResponse.success("同步成功");
    }
}
