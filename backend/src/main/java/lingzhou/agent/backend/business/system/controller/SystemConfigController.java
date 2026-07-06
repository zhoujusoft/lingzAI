package lingzhou.agent.backend.business.system.controller;

import jakarta.servlet.http.HttpServletRequest;
import lingzhou.agent.backend.business.BaseController;
import lingzhou.agent.backend.business.system.model.BrandingLogoUploadResult;
import lingzhou.agent.backend.business.system.model.BrandingSettingsDto;
import lingzhou.agent.backend.business.system.model.PlatformSettingsDto;
import lingzhou.agent.backend.business.system.model.TokenQuotaSettingsDto;
import lingzhou.agent.backend.business.system.model.UpdateBrandingSettingsInput;
import lingzhou.agent.backend.business.system.model.UpdatePlatformSettingsInput;
import lingzhou.agent.backend.business.system.model.UpdateTokenQuotaSettingsInput;
import lingzhou.agent.backend.business.system.service.SystemConfigService;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.framework.authentication.annotation.NotLogin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/systemConfig")
public class SystemConfigController extends BaseController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @PostMapping("/platforms/get")
    public ApiResponse<PlatformSettingsDto> getPlatformSettings(HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        try {
            return ApiResponse.success(systemConfigService.getPlatformSettings(operatorUserId));
        } catch (TaskException ex) {
            return ApiResponse.fail(400001, ex.getMessage());
        }
    }

    @PostMapping("/platforms/save")
    public ApiResponse<PlatformSettingsDto> savePlatformSettings(
            @RequestBody UpdatePlatformSettingsInput input, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        try {
            return ApiResponse.success(systemConfigService.savePlatformSettings(operatorUserId, input));
        } catch (TaskException ex) {
            return ApiResponse.fail(400001, ex.getMessage());
        }
    }

    @PostMapping("/tokenQuota/get")
    public ApiResponse<TokenQuotaSettingsDto> getTokenQuotaSettings(HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        try {
            return ApiResponse.success(systemConfigService.getTokenQuotaSettings(operatorUserId));
        } catch (TaskException ex) {
            return ApiResponse.fail(400001, ex.getMessage());
        }
    }

    @PostMapping("/tokenQuota/save")
    public ApiResponse<TokenQuotaSettingsDto> saveTokenQuotaSettings(
            @RequestBody UpdateTokenQuotaSettingsInput input, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        try {
            return ApiResponse.success(systemConfigService.saveTokenQuotaSettings(operatorUserId, input));
        } catch (TaskException ex) {
            return ApiResponse.fail(400001, ex.getMessage());
        }
    }

    @PostMapping("/branding/get")
    @NotLogin
    public ApiResponse<BrandingSettingsDto> getBrandingSettings() {
        return ApiResponse.success(systemConfigService.getBrandingSettings());
    }

    @PostMapping("/branding/save")
    public ApiResponse<BrandingSettingsDto> saveBrandingSettings(
            @RequestBody UpdateBrandingSettingsInput input, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        try {
            return ApiResponse.success(systemConfigService.saveBrandingSettings(operatorUserId, input));
        } catch (TaskException ex) {
            return ApiResponse.fail(400001, ex.getMessage());
        }
    }

    @PostMapping("/branding/uploadLogo")
    public ApiResponse<BrandingLogoUploadResult> uploadBrandingLogo(
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        try {
            return ApiResponse.success(systemConfigService.uploadBrandingLogo(operatorUserId, file));
        } catch (TaskException ex) {
            return ApiResponse.fail(400001, ex.getMessage());
        }
    }

    private static Long resolveCurrentUserId(HttpServletRequest request) {
        Object userIdValue = request.getAttribute("UserId");
        if (userIdValue == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(userIdValue));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
