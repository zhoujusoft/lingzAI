package lingzhou.agent.backend.business.system.controller;

import jakarta.servlet.http.HttpServletRequest;
import lingzhou.agent.backend.business.BaseController;
import lingzhou.agent.backend.business.system.model.ChangeCurrentUserPasswordInput;
import lingzhou.agent.backend.business.system.model.CreateUserInput;
import lingzhou.agent.backend.business.system.model.DeleteUserInput;
import lingzhou.agent.backend.business.system.model.GrantUserTokenQuotaInput;
import lingzhou.agent.backend.business.system.model.LoginCaptchaDto;
import lingzhou.agent.backend.business.system.model.ResetUserPasswordInput;
import lingzhou.agent.backend.business.system.model.SsoExchangeTokenInput;
import lingzhou.agent.backend.business.system.model.SsoExchangeTokenResult;
import lingzhou.agent.backend.business.system.model.UpdateUserProfileInput;
import lingzhou.agent.backend.business.system.model.UpdateUserStateInput;
import lingzhou.agent.backend.business.system.model.UpdateUserTokenQuotaInput;
import lingzhou.agent.backend.business.system.model.UserAvatarUploadResult;
import lingzhou.agent.backend.business.system.model.UserInfoDto;
import lingzhou.agent.backend.business.system.model.UserPageInput;
import lingzhou.agent.backend.business.system.model.UserPageResult;
import lingzhou.agent.backend.business.system.model.UserTokenQuotaSummaryDto;
import lingzhou.agent.backend.business.system.service.LoginCaptchaService;
import lingzhou.agent.backend.business.system.service.UserService;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.common.login.DomainLoginDto;
import lingzhou.agent.backend.common.login.GetOrganizationListInput;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.common.permission.AuthToken;
import lingzhou.agent.backend.common.permission.Const;
import lingzhou.agent.backend.framework.authentication.annotation.BypassLicense;
import lingzhou.agent.backend.framework.authentication.annotation.NotLogin;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
public class UserController extends BaseController {

    private final UserService userService;
    private final LoginCaptchaService loginCaptchaService;

    public UserController(UserService userService, LoginCaptchaService loginCaptchaService) {
        this.userService = userService;
        this.loginCaptchaService = loginCaptchaService;
    }

    @NotLogin
    @PostMapping("/getUseStateForLogin")
    public ApiResponse<DomainLoginDto> getUseStateForLogin(@RequestBody GetOrganizationListInput input)
            throws Exception {
        String captchaError = loginCaptchaService.validateCaptcha(
                input == null ? null : input.getCaptchaKey(), input == null ? null : input.getCaptchaCode());
        if (StringUtils.isNotBlank(captchaError)) {
            return ApiResponse.fail(400001, captchaError);
        }
        return ApiResponse.success(userService.login(input));
    }

    @NotLogin
    @GetMapping("/loginCaptcha")
    public ApiResponse<LoginCaptchaDto> getLoginCaptcha() {
        return ApiResponse.success(loginCaptchaService.createCaptcha());
    }

    @NotLogin
    @PostMapping("/refreshToken")
    public AuthToken refreshToken(HttpServletRequest request) throws Exception {
        String refreshToken = request.getHeader(Const.XRefreshToken);
        if (StringUtils.isBlank(refreshToken)) {
            refreshToken = request.getParameter(Const.XRefreshToken);
        }
        if (StringUtils.isBlank(refreshToken)) {
            refreshToken = request.getHeader(jwtUtils.getHeader());
        }
        refreshToken = normalizeToken(refreshToken);
        if (StringUtils.isBlank(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token is required");
        }
        return userService.refreshToken(refreshToken);
    }

    @NotLogin
    @PostMapping("/sso/exchange-token")
    public ApiResponse<SsoExchangeTokenResult> exchangeToken(@RequestBody SsoExchangeTokenInput input)
            throws Exception {
        return userService.exchangeToken(input);
    }

    @BypassLicense
    @GetMapping("/info")
    public ResponseEntity<UserInfoDto> getCurrentUserInfo(HttpServletRequest request) {
        Object userIdValue = request.getAttribute("UserId");
        if (userIdValue == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long userId;
        try {
            userId = Long.parseLong(String.valueOf(userIdValue));
        } catch (NumberFormatException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserInfoDto userInfo = userService.getUserInfoById(userId);
        if (userInfo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/tokenQuota")
    public ApiResponse<UserTokenQuotaSummaryDto> getCurrentUserTokenQuota(HttpServletRequest request) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        UserInfoDto userInfo = userService.getUserInfoById(userId);
        if (userInfo == null) {
            return ApiResponse.fail(404, "用户不存在");
        }
        return ApiResponse.success(userInfo.getTokenQuota());
    }

    @PostMapping("/list")
    public UserPageResult listUsers(@RequestBody(required = false) UserPageInput input, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        return userService.listUsers(operatorUserId, input);
    }

    @PostMapping("/create")
    public ApiResponse<Void> createUser(@RequestBody CreateUserInput input, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        String errorMessage = userService.createUser(operatorUserId, input);
        if (StringUtils.isNotBlank(errorMessage)) {
            return ApiResponse.fail(400001, errorMessage);
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/updateProfile")
    public ApiResponse<Void> updateUserProfile(@RequestBody UpdateUserProfileInput input, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        String errorMessage = userService.updateUserProfile(operatorUserId, input);
        if (StringUtils.isNotBlank(errorMessage)) {
            return ApiResponse.fail(400001, errorMessage);
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/resetPassword")
    public ApiResponse<Void> resetUserPassword(@RequestBody ResetUserPasswordInput input, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        String errorMessage = userService.resetUserPassword(operatorUserId, input);
        if (StringUtils.isNotBlank(errorMessage)) {
            return ApiResponse.fail(400001, errorMessage);
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/changePassword")
    public ApiResponse<Void> changeCurrentUserPassword(
            @RequestBody ChangeCurrentUserPasswordInput input, HttpServletRequest request) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        String errorMessage = userService.changeCurrentUserPassword(userId, input);
        if (StringUtils.isNotBlank(errorMessage)) {
            return ApiResponse.fail(400001, errorMessage);
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/profile/avatar/upload")
    public ApiResponse<UserAvatarUploadResult> uploadCurrentUserAvatar(
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        Long userId = resolveCurrentUserId(request);
        if (userId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        try {
            return ApiResponse.success(userService.uploadCurrentUserAvatar(userId, file));
        } catch (TaskException ex) {
            return ApiResponse.fail(400001, ex.getMessage());
        }
    }

    @PostMapping("/updateState")
    public ApiResponse<Void> updateUserState(@RequestBody UpdateUserStateInput input, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        String errorMessage = userService.updateUserState(operatorUserId, input);
        if (StringUtils.isNotBlank(errorMessage)) {
            return ApiResponse.fail(400001, errorMessage);
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> deleteUser(@RequestBody DeleteUserInput input, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        String errorMessage = userService.deleteUser(operatorUserId, input);
        if (StringUtils.isNotBlank(errorMessage)) {
            return ApiResponse.fail(400001, errorMessage);
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/tokenQuota/grant")
    public ApiResponse<Void> grantUserTokenQuota(
            @RequestBody GrantUserTokenQuotaInput input, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        String errorMessage = userService.grantUserTokenQuota(operatorUserId, input);
        if (StringUtils.isNotBlank(errorMessage)) {
            return ApiResponse.fail(400001, errorMessage);
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/tokenQuota/update")
    public ApiResponse<Void> updateUserTokenQuota(
            @RequestBody UpdateUserTokenQuotaInput input, HttpServletRequest request) {
        Long operatorUserId = resolveCurrentUserId(request);
        if (operatorUserId == null) {
            return ApiResponse.fail(401, "未授权");
        }
        String errorMessage = userService.updateUserTokenQuota(operatorUserId, input);
        if (StringUtils.isNotBlank(errorMessage)) {
            return ApiResponse.fail(400001, errorMessage);
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = request.getHeader(jwtUtils.getHeader());
        if (StringUtils.isBlank(token)) {
            token = request.getParameter(jwtUtils.getHeader());
        }
        token = normalizeToken(token);
        if (StringUtils.isNotBlank(token)) {
            userService.logout(token);
        }
        String refreshToken = request.getHeader(Const.XRefreshToken);
        if (StringUtils.isBlank(refreshToken)) {
            refreshToken = request.getParameter(Const.XRefreshToken);
        }
        refreshToken = normalizeToken(refreshToken);
        if (StringUtils.isNotBlank(refreshToken)) {
            userService.logout(refreshToken);
        }
        return ResponseEntity.ok().build();
    }

    private static String normalizeToken(String token) {
        if (StringUtils.isBlank(token)) {
            return token;
        }
        String value = token.trim();
        if (value.startsWith("Bearer ")) {
            return value.substring("Bearer ".length()).trim();
        }
        return value;
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
