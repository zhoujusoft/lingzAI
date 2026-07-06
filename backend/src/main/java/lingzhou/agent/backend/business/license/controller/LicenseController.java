package lingzhou.agent.backend.business.license.controller;

import jakarta.servlet.http.HttpServletRequest;
import lingzhou.agent.backend.business.BaseController;
import lingzhou.agent.backend.business.license.model.LicenseImportResult;
import lingzhou.agent.backend.business.license.model.LicenseRequestView;
import lingzhou.agent.backend.business.license.model.LicenseStatusView;
import lingzhou.agent.backend.business.license.service.LicenseService;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.framework.authentication.annotation.BypassLicense;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/license")
public class LicenseController extends BaseController {

    private final LicenseService licenseService;

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @PostMapping("/status")
    @BypassLicense
    public ApiResponse<LicenseStatusView> status() {
        return ApiResponse.success(licenseService.getStatusView());
    }

    @PostMapping("/request")
    @BypassLicense
    public ApiResponse<LicenseRequestView> request() {
        return ApiResponse.success(licenseService.buildLicenseRequest());
    }

    @PostMapping("/import")
    @BypassLicense
    public ApiResponse<LicenseImportResult> importLicense(
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        if (!licenseService.isAdminUser(operatorUserId)) {
            return ApiResponse.fail(403, "仅管理员可导入 license");
        }
        try {
            return ApiResponse.success(licenseService.importLicense(operatorUserId, file));
        } catch (Exception ex) {
            return ApiResponse.fail(423001, StringUtils.defaultIfBlank(ex.getMessage(), "导入失败"));
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
