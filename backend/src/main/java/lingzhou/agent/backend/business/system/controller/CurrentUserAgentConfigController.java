package lingzhou.agent.backend.business.system.controller;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.BaseController;
import lingzhou.agent.backend.business.system.model.AgentSimpleDto;
import lingzhou.agent.backend.business.system.model.AgentDetailDto;
import lingzhou.agent.backend.business.system.model.UpdateUserAgentTemplateInput;
import lingzhou.agent.backend.business.system.model.UpdateUserAgentProfileInput;
import lingzhou.agent.backend.business.system.model.UpdateUserSkillPreferenceInput;
import lingzhou.agent.backend.business.system.model.SkillSimpleDto;
import lingzhou.agent.backend.business.system.model.UserAvatarUploadResult;
import lingzhou.agent.backend.business.system.model.UserAgentFile;
import lingzhou.agent.backend.business.system.model.UserSkillPreferenceDto;
import lingzhou.agent.backend.business.system.service.UserAgentConfigService;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user/agent-config")
public class CurrentUserAgentConfigController extends BaseController {

    private final UserAgentConfigService userAgentConfigService;

    public CurrentUserAgentConfigController(UserAgentConfigService userAgentConfigService) {
        this.userAgentConfigService = userAgentConfigService;
    }

    @GetMapping("/template")
    public ApiResponse<AgentDetailDto> getCurrentUserTemplate() {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.ensureUserConfig(userId, null);
        return ApiResponse.success(userAgentConfigService.getUserAgentTemplate(userId));
    }

    @GetMapping("/templates")
    public ApiResponse<List<AgentSimpleDto>> listCurrentUserAvailableTemplates() {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        return ApiResponse.success(userAgentConfigService.listAvailableAgents());
    }

    @PutMapping("/template")
    public ApiResponse<String> updateCurrentUserTemplate(@RequestBody UpdateUserAgentTemplateInput input) {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.updateUserAgentTemplate(userId, input == null ? null : input.getAgentId());
        return ApiResponse.success("更新成功");
    }

    @PutMapping("/assistant")
    public ApiResponse<String> updateCurrentUserAssistant(@RequestBody UpdateUserAgentProfileInput input) {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.updateUserAgentProfile(userId, input == null ? null : input.getAgentName());
        return ApiResponse.success("更新成功");
    }

    @PostMapping("/assistant/avatar/upload")
    public ApiResponse<UserAvatarUploadResult> uploadCurrentUserAssistantAvatar(
            @RequestPart("file") MultipartFile file) {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        try {
            return ApiResponse.success(userAgentConfigService.uploadUserAgentAvatar(userId, file));
        } catch (TaskException ex) {
            return ApiResponse.fail(400001, ex.getMessage());
        }
    }

    @GetMapping("/file/{filename}")
    public ApiResponse<UserAgentFile> getCurrentUserFile(@PathVariable("filename") String filename) {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.ensureUserConfig(userId, null);
        return ApiResponse.success(userAgentConfigService.getUserAgentFile(userId, filename));
    }

    @PutMapping("/file/{filename}")
    public ApiResponse<String> updateCurrentUserFile(
            @PathVariable("filename") String filename, @RequestBody Map<String, String> body) {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.updateUserAgentFile(userId, filename, body.get("content"));
        return ApiResponse.success("更新成功");
    }

    @PutMapping("/profile")
    public ApiResponse<String> updateCurrentUserProfile(@RequestBody Map<String, String> body) {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.updateUserProfile(userId, body.get("profileContent"));
        return ApiResponse.success("更新成功");
    }

    @GetMapping("/skills")
    public ApiResponse<List<Long>> getCurrentUserSkills() {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.ensureUserConfig(userId, null);
        return ApiResponse.success(userAgentConfigService.getUserSkillIds(userId));
    }

    @GetMapping("/skills/detail")
    public ApiResponse<List<SkillSimpleDto>> getCurrentUserSkillDetails() {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.ensureUserConfig(userId, null);
        return ApiResponse.success(userAgentConfigService.getUserSkills(userId));
    }

    @GetMapping("/skills/preference")
    public ApiResponse<UserSkillPreferenceDto> getCurrentUserSkillPreference() {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.ensureUserConfig(userId, null);
        return ApiResponse.success(userAgentConfigService.getUserSkillPreference(userId));
    }

    @PutMapping("/skills/preference")
    public ApiResponse<String> updateCurrentUserSkillPreference(@RequestBody UpdateUserSkillPreferenceInput input) {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.updateUserSkillPreference(
                userId, input == null ? List.of() : input.getEnabledSkillIds());
        return ApiResponse.success("更新成功");
    }

    @PostMapping("/skill/{skillId}")
    public ApiResponse<String> addCurrentUserSkill(@PathVariable("skillId") Long skillId) {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.addUserSkillBinding(userId, skillId);
        return ApiResponse.success("添加成功");
    }

    @DeleteMapping("/skill/{skillId}")
    public ApiResponse<String> removeCurrentUserSkill(@PathVariable("skillId") Long skillId) {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.removeUserSkillBinding(userId, skillId);
        return ApiResponse.success("移除成功");
    }

    @PostMapping("/sync")
    public ApiResponse<String> syncCurrentUserConfig() {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        userAgentConfigService.syncUserConfig(userId, null);
        return ApiResponse.success("同步成功");
    }

    private Long resolveCurrentUserId() {
        try {
            String userId = getUserId();
            if (userId == null || userId.isBlank()) {
                return null;
            }
            return Long.parseLong(userId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
