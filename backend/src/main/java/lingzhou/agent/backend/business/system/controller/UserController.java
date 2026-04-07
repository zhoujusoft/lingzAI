package lingzhou.agent.backend.business.system.controller;

import jakarta.servlet.http.HttpServletRequest;
import lingzhou.agent.backend.business.BaseController;
import lingzhou.agent.backend.business.system.model.CreateUserInput;
import lingzhou.agent.backend.business.system.model.DeleteUserInput;
import lingzhou.agent.backend.business.system.model.ResetUserPasswordInput;
import lingzhou.agent.backend.business.system.model.SsoExchangeTokenInput;
import lingzhou.agent.backend.business.system.model.SsoExchangeTokenResult;
import lingzhou.agent.backend.business.system.model.UpdateUserProfileInput;
import lingzhou.agent.backend.business.system.model.UpdateUserStateInput;
import lingzhou.agent.backend.business.system.model.UserInfoDto;
import lingzhou.agent.backend.business.system.model.UserPageInput;
import lingzhou.agent.backend.business.system.model.UserPageResult;
import lingzhou.agent.backend.business.system.service.UserService;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.common.login.DomainLoginDto;
import lingzhou.agent.backend.common.login.GetOrganizationListInput;
import lingzhou.agent.backend.common.permission.AuthToken;
import lingzhou.agent.backend.common.permission.Const;
import lingzhou.agent.backend.framework.authentication.annotation.NotLogin;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/user")
public class UserController extends BaseController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @NotLogin
    @PostMapping("/getUseStateForLogin")
    public DomainLoginDto getUseStateForLogin(@RequestBody GetOrganizationListInput input, HttpServletRequest request)
            throws Exception {
        return userService.login(input);
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
