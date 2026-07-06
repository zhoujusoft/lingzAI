package lingzhou.agent.backend.business.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.io.IOException;
import lingzhou.agent.backend.business.chat.execution.workspace.WorkspaceBootstrapService;
import lingzhou.agent.backend.business.chat.service.UserTokenQuotaService;
import lingzhou.agent.backend.business.license.service.LicenseService;
import lingzhou.agent.backend.business.system.dao.ExternalTokenExchangeLogMapper;
import lingzhou.agent.backend.business.system.dao.SysRoleMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.AgentSimpleDto;
import lingzhou.agent.backend.business.system.model.ChangeCurrentUserPasswordInput;
import lingzhou.agent.backend.business.system.model.CreateUserInput;
import lingzhou.agent.backend.business.system.model.DeleteUserInput;
import lingzhou.agent.backend.business.system.model.ExternalTokenExchangeLogModel;
import lingzhou.agent.backend.business.system.model.GrantUserTokenQuotaInput;
import lingzhou.agent.backend.business.system.model.ResetUserPasswordInput;
import lingzhou.agent.backend.business.system.model.SsoExchangeTokenInput;
import lingzhou.agent.backend.business.system.model.SsoExchangeTokenResult;
import lingzhou.agent.backend.business.system.model.SysRole;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.system.model.UpdateUserProfileInput;
import lingzhou.agent.backend.business.system.model.UpdateUserStateInput;
import lingzhou.agent.backend.business.system.model.UpdateUserTokenQuotaInput;
import lingzhou.agent.backend.business.system.model.UserAvatarUploadResult;
import lingzhou.agent.backend.business.system.model.UserInfoDto;
import lingzhou.agent.backend.business.system.model.UserListItemDto;
import lingzhou.agent.backend.business.system.model.UserPageInput;
import lingzhou.agent.backend.business.system.model.UserPageResult;
import lingzhou.agent.backend.business.system.service.AgentTemplateService;
import lingzhou.agent.backend.business.system.service.RoleResourcePermissionService;
import lingzhou.agent.backend.business.system.service.RoleService;
import lingzhou.agent.backend.business.system.service.UserAgentConfigService;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import lingzhou.agent.backend.business.system.service.UserService;
import lingzhou.agent.backend.common.api.ApiResponse;
import lingzhou.agent.backend.common.enums.LoginInfoState;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.common.login.DomainLoginDto;
import lingzhou.agent.backend.common.login.GetOrganizationListInput;
import lingzhou.agent.backend.common.permission.AuthToken;
import lingzhou.agent.backend.common.permission.Const;
import lingzhou.agent.backend.framework.authentication.JwtUtils;
import lingzhou.agent.backend.framework.authentication.SsoNonceService;
import lingzhou.agent.backend.framework.authentication.SsoSignUtils;
import lingzhou.agent.backend.framework.authentication.TokenBlacklistService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lingzhou.agent.backend.common.utils.ImageSignatureUtils;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final long MAX_AVATAR_FILE_SIZE = 2 * 1024 * 1024;
    private static final Set<String> SUPPORTED_AVATAR_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png");
    private static final int SSO_ERR_USER_NOT_FOUND = 401001;
    private static final int SSO_ERR_MULTIPLE_USERS = 401002;
    private static final int SSO_ERR_INVALID_PHONE = 401003;
    private static final int SSO_ERR_INVALID_SIGN = 401004;
    private static final int SSO_ERR_EXPIRED = 401005;
    private static final int SSO_ERR_REPLAYED = 401006;
    private static final int SSO_ERR_MISSING_PARAM = 401007;
    private static final int SSO_ERR_USER_DISABLED = 401008;
    private static final int SSO_ERR_SOURCE_NOT_ALLOWED = 401010;

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final ExternalTokenExchangeLogMapper externalTokenExchangeLogMapper;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final SsoNonceService ssoNonceService;
    private final WorkspaceBootstrapService workspaceBootstrapService;
    private final RoleService roleService;
    private final AgentTemplateService agentTemplateService;
    private final UserAgentConfigService userAgentConfigService;
    private final UserTokenQuotaService userTokenQuotaService;
    private final MinioService minioService;
    private final LicenseService licenseService;
    private final RoleResourcePermissionService roleResourcePermissionService;

    @Value("${app.sso.enabled:true}")
    private boolean ssoEnabled;

    @Value("${app.sso.shared-secret:change-me-sso-secret}")
    private String ssoSharedSecret;

    @Value("${app.sso.request-expire-seconds:300}")
    private long ssoRequestExpireSeconds;

    @Value("${app.sso.nonce-expire-seconds:300}")
    private long ssoNonceExpireSeconds;

    @Value("${app.sso.allowed-source-systems:oa}")
    private String ssoAllowedSourceSystems;

    @Value("${app.version:1.3.7}")
    private String appVersion;

    public UserServiceImpl(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            ExternalTokenExchangeLogMapper externalTokenExchangeLogMapper,
            JwtUtils jwtUtils,
            TokenBlacklistService tokenBlacklistService,
            SsoNonceService ssoNonceService,
            WorkspaceBootstrapService workspaceBootstrapService,
            RoleService roleService,
            AgentTemplateService agentTemplateService,
            UserAgentConfigService userAgentConfigService,
            UserTokenQuotaService userTokenQuotaService,
            MinioService minioService,
            LicenseService licenseService,
            RoleResourcePermissionService roleResourcePermissionService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.externalTokenExchangeLogMapper = externalTokenExchangeLogMapper;
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
        this.ssoNonceService = ssoNonceService;
        this.workspaceBootstrapService = workspaceBootstrapService;
        this.roleService = roleService;
        this.agentTemplateService = agentTemplateService;
        this.userAgentConfigService = userAgentConfigService;
        this.userTokenQuotaService = userTokenQuotaService;
        this.minioService = minioService;
        this.licenseService = licenseService;
        this.roleResourcePermissionService = roleResourcePermissionService;
    }

    @Override
    public DomainLoginDto login(GetOrganizationListInput input) throws Exception {
        DomainLoginDto login = new DomainLoginDto();

        if (input == null || StringUtils.isBlank(input.getCode())) {
            login.setState(LoginInfoState.NoPass);
            return login;
        }

        SysUserModel user = sysUserMapper.selectByCode(input.getCode());
        if (user == null) {
            login.setState(LoginInfoState.NoPass);
            return login;
        }

        if (user.getState() != null && user.getState() != 1) {
            login.setState(LoginInfoState.UserDisable);
            return login;
        }

        // 登录密码要求为前端 md5 密文（32位hex，不带前缀）。
        String requestPasswordMd5Hex = decodeIncomingPassword(input.getPassword(), false);
        if (StringUtils.isBlank(requestPasswordMd5Hex)) {
            login.setState(LoginInfoState.NoPass);
            return login;
        }

        // 数据库存储为 md5 32位hex（不带前缀）。
        String storedPasswordMd5Hex = decodeIncomingPassword(user.getPassword(), false);
        boolean passwordMatches = StringUtils.isNotBlank(storedPasswordMd5Hex)
                && StringUtils.equalsIgnoreCase(requestPasswordMd5Hex, storedPasswordMd5Hex);
        if (!passwordMatches) {
            login.setState(LoginInfoState.NoPass);
            return login;
        }

        jwtUtils.setExpire(Const.AccessTokenExpireSeconds);
        AuthToken authToken =
                jwtUtils.createTokenPair(String.valueOf(user.getId()), user.getCode(), input.isRememberMe());
        login.setAccessToken(authToken.getAccessToken());
        login.setRefreshToken(authToken.getRefreshToken());
        login.setExUserId("");
        login.setExDingTalkId("");
        login.setState(LoginInfoState.Normal);

        // 确保用户有 Agent 配置
        userAgentConfigService.ensureUserConfig(user.getId(), user.getRoleId());

        return login;
    }

    @Override
    public AuthToken refreshToken(String refreshToken) throws Exception {
        return jwtUtils.refreshToken(refreshToken);
    }

    @Override
    public ApiResponse<SsoExchangeTokenResult> exchangeToken(SsoExchangeTokenInput input) throws Exception {
        if (!ssoEnabled) {
            return ApiResponse.fail(SSO_ERR_SOURCE_NOT_ALLOWED, "身份交换功能未开启");
        }
        if (input == null || hasMissingSsoFields(input)) {
            return failAndLog(input, null, "", "INVALID_PARAM", SSO_ERR_MISSING_PARAM, "请求参数不完整");
        }
        if (!isAllowedSourceSystem(input.getSourceSystem())) {
            return failAndLog(
                    input,
                    null,
                    normalizePhone(input.getPhone()),
                    "SOURCE_NOT_ALLOWED",
                    SSO_ERR_SOURCE_NOT_ALLOWED,
                    "来源系统不允许");
        }
        if (isExpiredTimestamp(input.getTimestamp())) {
            return failAndLog(input, null, normalizePhone(input.getPhone()), "EXPIRED", SSO_ERR_EXPIRED, "请求已过期");
        }
        String nonceKey = buildNonceKey(input.getSourceSystem(), input.getNonce());
        if (!ssoNonceService.tryAcquire(nonceKey, ssoNonceExpireSeconds)) {
            return failAndLog(input, null, normalizePhone(input.getPhone()), "REPLAYED", SSO_ERR_REPLAYED, "请求已失效");
        }
        String payload = SsoSignUtils.buildPayload(
                input.getSourceSystem(),
                input.getExternalUserId(),
                input.getPhone(),
                input.getTimestamp(),
                input.getNonce());
        if (!SsoSignUtils.verify(payload, ssoSharedSecret, input.getSign())) {
            return failAndLog(
                    input, null, normalizePhone(input.getPhone()), "SIGN_INVALID", SSO_ERR_INVALID_SIGN, "签名校验失败");
        }

        String normalizedPhone = normalizePhone(input.getPhone());
        if (!isValidPhone(normalizedPhone)) {
            return failAndLog(input, null, normalizedPhone, "INVALID_PHONE", SSO_ERR_INVALID_PHONE, "手机号格式不合法");
        }

        List<SysUserModel> matchedUsers = findUsersByNormalizedMobile(normalizedPhone);
        if (matchedUsers.isEmpty()) {
            return failAndLog(input, null, normalizedPhone, "NOT_FOUND", SSO_ERR_USER_NOT_FOUND, "手机号未匹配到本地用户");
        }
        if (matchedUsers.size() > 1) {
            return failAndLog(input, null, normalizedPhone, "MULTIPLE", SSO_ERR_MULTIPLE_USERS, "手机号匹配到多个本地用户");
        }

        SysUserModel user = matchedUsers.get(0);
        if (user.getState() == null || user.getState() != 1) {
            return failAndLog(input, user.getId(), normalizedPhone, "USER_DISABLED", SSO_ERR_USER_DISABLED, "本地用户已禁用");
        }

        AuthToken authToken = jwtUtils.createTokenPair(String.valueOf(user.getId()), user.getCode(), false);
        SsoExchangeTokenResult result = new SsoExchangeTokenResult();
        result.setAccessToken(authToken.getAccessToken());
        result.setRefreshToken(authToken.getRefreshToken());
        result.setExpiresIn(Const.AccessTokenExpireSeconds);
        result.setRefreshExpiresIn(Const.RefreshTokenExpireSecondsDefault);
        result.setUserId(user.getId());
        result.setUserCode(user.getCode());

        saveExchangeLog(input, normalizedPhone, user.getId(), "SUCCESS", "换取 token 成功");
        log.info(
                "SSO token 交换成功：sourceSystem={}, externalUserId={}, userId={}",
                safeTrim(input.getSourceSystem()),
                safeTrim(input.getExternalUserId()),
                user.getId());
        return ApiResponse.success(result);
    }

    @Override
    public String createUser(Long operatorUserId, CreateUserInput input) {
        if (operatorUserId == null) {
            return "未授权";
        }
        if (input == null) {
            return "请求参数不能为空";
        }
        SysUserModel operator = sysUserMapper.selectById(operatorUserId);
        if (!isAdminUser(operator)) {
            return "普通用户不可新增用户";
        }
        return doCreateUser(input);
    }

    @Override
    public String createUserWithoutPermissionCheck(CreateUserInput input) {
        if (input == null) {
            return "请求参数不能为空";
        }
        return doCreateUser(input);
    }

    private String doCreateUser(CreateUserInput input) {
        try {
            licenseService.assertUserCreationAllowed();
        } catch (RuntimeException ex) {
            return ex.getMessage();
        }
        String name = normalizeText(input.getName());
        String account = normalizeText(input.getAccount());
        String encryptedPassword = normalizeText(input.getPassword());
        String mobile = normalizeText(input.getMobile());
        String email = normalizeText(input.getEmail());

        // 新增用户：接口接收 md5 密文（32位hex，不带前缀）。
        String passwordMd5Hex = decodeIncomingPassword(encryptedPassword, false);
        if (passwordMd5Hex == null) {
            return "密码加密格式不正确";
        }
        passwordMd5Hex = normalizeText(passwordMd5Hex);

        String validationError = validateUserForm(name, account, passwordMd5Hex);
        if (validationError != null) {
            return validationError;
        }
        String contactValidationError = validateOptionalContact(mobile, email);
        if (contactValidationError != null) {
            return contactValidationError;
        }
        if (isAccountTakenByOther(account, null)) {
            return "登录名已存在";
        }

        SysUserModel user = new SysUserModel();
        String applyError = applyUserForm(user, name, account, passwordMd5Hex, mobile, email, input.getUserType());
        if (applyError != null) {
            return applyError;
        }
        user.setState(1);
        // 设置角色（校验角色可用性）
        if (input.getRoleId() != null) {
            String roleError = roleService.checkRoleUsable(input.getRoleId());
            if (roleError != null) {
                return roleError;
            }
            user.setRoleId(input.getRoleId());
        }

        int affectedRows = sysUserMapper.insert(user);
        if (affectedRows <= 0) {
            return "新增用户失败";
        }
        try {
            workspaceBootstrapService.ensureUserProfile(user.getId());
            userAgentConfigService.syncUserConfig(user.getId(), user.getRoleId());
            userTokenQuotaService.initializeAccountForUser(user.getId());
        } catch (RuntimeException ex) {
            log.error("初始化用户工作区或Agent配置失败：userId={}, error={}", user.getId(), ex.getMessage(), ex);
            userTokenQuotaService.deleteAccountByUserId(user.getId());
            sysUserMapper.deleteById(user.getId());
            return "初始化用户工作区或Agent配置失败";
        }

        return null;
    }

    @Override
    public String updateUserProfile(Long operatorUserId, UpdateUserProfileInput input) {
        if (operatorUserId == null) {
            return "未授权";
        }
        if (input == null || input.getId() == null) {
            return "用户ID不能为空";
        }

        String name = normalizeText(input.getName());
        String mobile = normalizeText(input.getMobile());
        String email = normalizeText(input.getEmail());
        Integer userType = input.getUserType();
        String encryptedPassword = normalizeText(input.getPassword());

        if (StringUtils.isBlank(name)) {
            return "姓名不能为空";
        }
        String contactValidationError = validateOptionalContact(mobile, email);
        if (contactValidationError != null) {
            return contactValidationError;
        }

        SysUserModel operator = sysUserMapper.selectById(operatorUserId);
        if (operator == null) {
            return "未授权";
        }
        boolean adminOperator = isAdminUser(operator);

        SysUserModel user = sysUserMapper.selectById(input.getId());
        if (user == null) {
            return "用户不存在";
        }
        if (!adminOperator && !user.getId().equals(operatorUserId)) {
            return "普通用户仅可修改自己的信息";
        }
        Long originalRoleId = user.getRoleId();

        user.setName(name);
        user.setMobile(StringUtils.isBlank(mobile) ? null : mobile);
        user.setEmail(StringUtils.isBlank(email) ? null : email);
        if (adminOperator) {
            if (userType == null) {
                return "用户类型不能为空";
            }
            user.setUserType(userType);
            // 管理员可以设置角色（校验角色可用性）
            if (input.getRoleId() != null) {
                String roleError = roleService.checkRoleUsable(input.getRoleId());
                if (roleError != null) {
                    return roleError;
                }
                user.setRoleId(input.getRoleId());
            } else {
                user.setRoleId(null);
            }
        }

        // 编辑资料时允许可选重置密码：仅管理员可执行密码更新。
        if (StringUtils.isNotBlank(encryptedPassword)) {
            if (!adminOperator) {
                return "仅管理员可重置密码";
            }
            String passwordMd5Hex = decodeIncomingPassword(encryptedPassword, false);
            if (passwordMd5Hex == null || StringUtils.isBlank(passwordMd5Hex)) {
                return "密码加密格式不正确";
            }
            passwordMd5Hex = normalizeText(passwordMd5Hex);
            String passwordValidationError = validatePasswordPolicy(passwordMd5Hex);
            if (passwordValidationError != null) {
                return passwordValidationError;
            }
            user.setPassword(formatStoredPassword(passwordMd5Hex));
        }

        int affectedRows = sysUserMapper.updateById(user);
        if (affectedRows <= 0) {
            return "编辑用户失败";
        }
        if (adminOperator && !Objects.equals(originalRoleId, user.getRoleId())) {
            try {
                userAgentConfigService.syncUserConfig(user.getId(), user.getRoleId());
            } catch (RuntimeException ex) {
                log.error(
                        "同步用户 Agent 模板配置失败：userId={}, roleId={}, error={}",
                        user.getId(),
                        user.getRoleId(),
                        ex.getMessage(),
                        ex);
                return "同步用户Agent模板配置失败";
            }
        }

        return null;
    }

    @Override
    public String resetUserPassword(Long operatorUserId, ResetUserPasswordInput input) {
        if (operatorUserId == null) {
            return "未授权";
        }
        if (input == null || input.getId() == null) {
            return "用户ID不能为空";
        }

        SysUserModel operator = sysUserMapper.selectById(operatorUserId);
        if (operator == null) {
            return "未授权";
        }
        if (!isAdminUser(operator)) {
            return "仅管理员可重置密码";
        }

        SysUserModel target = sysUserMapper.selectById(input.getId());
        if (target == null) {
            return "用户不存在";
        }

        String encryptedPassword = normalizeText(input.getPassword());
        String passwordMd5Hex = decodeIncomingPassword(encryptedPassword, false);
        if (passwordMd5Hex == null || StringUtils.isBlank(passwordMd5Hex)) {
            return "密码加密格式不正确";
        }
        passwordMd5Hex = normalizeText(passwordMd5Hex);

        String passwordValidationError = validatePasswordPolicy(passwordMd5Hex);
        if (passwordValidationError != null) {
            return passwordValidationError;
        }

        target.setPassword(formatStoredPassword(passwordMd5Hex));
        int affectedRows = sysUserMapper.updateById(target);
        if (affectedRows <= 0) {
            return "重置密码失败";
        }
        return null;
    }

    @Override
    public String changeCurrentUserPassword(Long userId, ChangeCurrentUserPasswordInput input) {
        if (userId == null) {
            return "未授权";
        }
        if (input == null) {
            return "请求参数不能为空";
        }
        String encryptedOldPassword = normalizeText(input.getOldPassword());
        String encryptedNewPassword = normalizeText(input.getPassword());

        String oldPasswordMd5Hex = decodeIncomingPassword(encryptedOldPassword, false);
        if (oldPasswordMd5Hex == null || StringUtils.isBlank(oldPasswordMd5Hex)) {
            return "旧密码格式不正确";
        }
        oldPasswordMd5Hex = normalizeText(oldPasswordMd5Hex);

        String newPasswordMd5Hex = decodeIncomingPassword(encryptedNewPassword, false);
        if (newPasswordMd5Hex == null || StringUtils.isBlank(newPasswordMd5Hex)) {
            return "密码加密格式不正确";
        }
        newPasswordMd5Hex = normalizeText(newPasswordMd5Hex);

        String passwordValidationError = validatePasswordPolicy(newPasswordMd5Hex);
        if (passwordValidationError != null) {
            return passwordValidationError;
        }

        SysUserModel user = sysUserMapper.selectById(userId);
        if (user == null) {
            return "用户不存在";
        }
        String storedPasswordMd5Hex = decodeIncomingPassword(user.getPassword(), false);
        if (StringUtils.isBlank(storedPasswordMd5Hex)
                || !StringUtils.equalsIgnoreCase(storedPasswordMd5Hex, oldPasswordMd5Hex)) {
            return "旧密码错误";
        }

        user.setPassword(formatStoredPassword(newPasswordMd5Hex));
        int affectedRows = sysUserMapper.updateById(user);
        if (affectedRows <= 0) {
            return "修改密码失败";
        }
        return null;
    }

    @Override
    public String updateUserState(Long operatorUserId, UpdateUserStateInput input) {
        if (operatorUserId == null) {
            return "未授权";
        }
        if (input == null || input.getId() == null) {
            return "用户ID不能为空";
        }
        if (input.getState() == null) {
            return "状态不能为空";
        }
        if (input.getState() != 0 && input.getState() != 1) {
            return "状态值不合法";
        }

        SysUserModel operator = sysUserMapper.selectById(operatorUserId);
        if (operator == null) {
            return "未授权";
        }
        if (!isAdminUser(operator)) {
            return "普通用户不可禁用或删除用户";
        }

        SysUserModel target = sysUserMapper.selectById(input.getId());
        if (target == null) {
            return "用户不存在";
        }
        if (isFixedAdmin(target)) {
            return "admin用户不可禁用";
        }

        if (input.getState() == 1 && (target.getState() == null || target.getState() != 1)) {
            try {
                licenseService.assertUserCreationAllowed();
            } catch (RuntimeException ex) {
                return ex.getMessage();
            }
        }
        target.setState(input.getState());
        int affectedRows = sysUserMapper.updateById(target);
        if (affectedRows <= 0) {
            return "更新用户状态失败";
        }
        return null;
    }

    @Override
    public String deleteUser(Long operatorUserId, DeleteUserInput input) {
        if (operatorUserId == null) {
            return "未授权";
        }
        if (input == null || input.getId() == null) {
            return "用户ID不能为空";
        }

        SysUserModel operator = sysUserMapper.selectById(operatorUserId);
        if (operator == null) {
            return "未授权";
        }
        if (!isAdminUser(operator)) {
            return "普通用户不可禁用或删除用户";
        }

        SysUserModel target = sysUserMapper.selectById(input.getId());
        if (target == null) {
            return "用户不存在";
        }
        if (isFixedAdmin(target)) {
            return "admin用户不可删除";
        }

        int affectedRows = sysUserMapper.deleteById(input.getId());
        if (affectedRows <= 0) {
            return "删除用户失败";
        }
        return null;
    }

    @Override
    public UserAvatarUploadResult uploadCurrentUserAvatar(Long userId, MultipartFile file) throws TaskException {
        if (userId == null) {
            throw new TaskException("未授权", TaskException.Code.UNKNOWN);
        }
        if (file == null || file.isEmpty()) {
            throw new TaskException("请先选择头像文件", TaskException.Code.UNKNOWN);
        }
        if (file.getSize() > MAX_AVATAR_FILE_SIZE) {
            throw new TaskException("头像文件不能超过 2MB", TaskException.Code.UNKNOWN);
        }

        SysUserModel user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new TaskException("用户不存在", TaskException.Code.UNKNOWN);
        }

        String originalName = StringUtils.trimToEmpty(file.getOriginalFilename());
        String requestedExtension = normalizeAvatarExtension(originalName);
        if (!SUPPORTED_AVATAR_EXTENSIONS.contains(requestedExtension)) {
            throw new TaskException("头像仅支持 JPG 或 PNG 格式", TaskException.Code.UNKNOWN);
        }

        byte[] content = readFileBytes(file);
        ImageSignatureUtils.SupportedImageType imageType = ImageSignatureUtils.detectSupportedImageType(content);
        if (imageType == null || !matchesAvatarExtension(requestedExtension, imageType)) {
            throw new TaskException("头像文件内容校验失败，请上传 JPG 或 PNG 图片", TaskException.Code.UNKNOWN);
        }

        long version = System.currentTimeMillis();
        String objectName = minioService.buildMediaObjectName(
                "users", user.getId(), "avatar", "avatar", version, imageType.extension());
        String previousObjectName = StringUtils.trimToNull(user.getAvatarObjectName());
        try {
            minioService.uploadObject(objectName, content, imageType.contentType());
        } catch (Exception ex) {
            log.warn("用户头像上传失败：userId={}, fileName={}, error={}", userId, originalName, ex.getMessage(), ex);
            throw new TaskException("头像上传失败，请稍后重试", TaskException.Code.UNKNOWN, asException(ex));
        }

        user.setAvatarObjectName(objectName);
        try {
            int affectedRows = sysUserMapper.updateById(user);
            if (affectedRows <= 0) {
                throw new TaskException("头像保存失败，请稍后重试", TaskException.Code.UNKNOWN);
            }
        } catch (Exception ex) {
            cleanupUploadedAvatar(objectName, userId, "头像保存失败后清理新文件");
            if (ex instanceof TaskException taskException) {
                throw taskException;
            }
            throw new TaskException("头像保存失败，请稍后重试", TaskException.Code.UNKNOWN, asException(ex));
        }

        cleanupPreviousAvatar(previousObjectName, objectName, userId);
        UserAvatarUploadResult result = new UserAvatarUploadResult();
        result.setAvatarObjectName(objectName);
        result.setAvatarUrl(minioService.buildObjectPreviewUrl(objectName));
        return result;
    }

    @Override
    public UserInfoDto getUserInfoById(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUserModel user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        UserInfoDto dto = new UserInfoDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setCode(user.getCode());
        dto.setMobile(user.getMobile());
        dto.setEmail(user.getEmail());
        if (StringUtils.isNotBlank(user.getAvatarObjectName())) {
            dto.setAvatarUrl(minioService.buildObjectPreviewUrl(user.getAvatarObjectName()));
        }
        dto.setUserType(user.getUserType());
        dto.setState(user.getState());
        dto.setAppVersion(appVersion);
        // 添加角色信息（处理角色已删除的情况）
        dto.setRoleId(user.getRoleId());
        if (user.getRoleId() != null) {
            SysRole role = sysRoleMapper.selectById(user.getRoleId());
            if (role != null) {
                dto.setRoleName(role.getRoleName());
                dto.setRoleCode(role.getRoleCode());
                // 查询角色绑定的 Agent
                if (role.getAgentId() != null) {
                    AgentSimpleDto agent = agentTemplateService.getAgentById(role.getAgentId());
                    if (agent != null) {
                        dto.setRoleAgent(agent);
                    }
                }
            } else {
                dto.setRoleName("(角色已删除)");
            }
        }
        if (isFixedAdmin(user)) {
            dto.setMenuPermissions(List.of("*"));
        } else if (user.getRoleId() != null) {
            dto.setMenuPermissions(roleService.listRoleMenuPermissions(user.getRoleId()));
        } else {
            dto.setMenuPermissions(List.of());
        }
        // 资源权限
        if (isFixedAdmin(user)) {
            dto.setResourcePermissionUnrestricted(true);
            dto.setPermittedSkillIds(List.of());
            dto.setPermittedToolIds(List.of());
        } else if (user.getRoleId() != null) {
            dto.setResourcePermissionUnrestricted(false);
            dto.setPermittedSkillIds(roleResourcePermissionService.getRoleSkillIds(user.getRoleId()));
            dto.setPermittedToolIds(roleResourcePermissionService.getRoleToolIds(user.getRoleId()));
        } else {
            dto.setResourcePermissionUnrestricted(false);
            dto.setPermittedSkillIds(List.of());
            dto.setPermittedToolIds(List.of());
        }
        dto.setTokenQuota(userTokenQuotaService.getQuotaSummary(user.getId()));
        return dto;
    }

    @Override
    public UserPageResult listUsers(Long operatorUserId, UserPageInput input) {
        if (operatorUserId == null) {
            return emptyPageResult(1, 10);
        }
        SysUserModel operator = sysUserMapper.selectById(operatorUserId);
        if (!isAdminUser(operator)) {
            return emptyPageResult(1, 10);
        }

        int requestedPage = input != null && input.getPage() != null ? input.getPage() : 1;
        int requestedPageSize = input != null && input.getPageSize() != null ? input.getPageSize() : 10;
        int safePage = Math.max(requestedPage, 1);
        int safePageSize = Math.max(1, Math.min(requestedPageSize, 100));
        String keyword = StringUtils.trimToEmpty(input != null ? input.getKeyword() : null);

        Page<SysUserModel> pageRequest = new Page<>(safePage, safePageSize);
        LambdaQueryWrapper<SysUserModel> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(SysUserModel::getCode, keyword)
                    .or()
                    .like(SysUserModel::getName, keyword)
                    .or()
                    .like(SysUserModel::getMobile, keyword)
                    .or()
                    .like(SysUserModel::getEmail, keyword));
        }
        wrapper.orderByDesc(SysUserModel::getId);
        IPage<SysUserModel> pageData = sysUserMapper.selectPage(pageRequest, wrapper);
        List<SysUserModel> users = pageData.getRecords();
        long total = pageData.getTotal();

        UserPageResult result = new UserPageResult();
        result.setItems(users.stream().map(this::toUserListItem).collect(Collectors.toList()));
        result.setTotal(total);
        result.setPage(safePage);
        result.setPageSize(safePageSize);
        return result;
    }

    @Override
    public String grantUserTokenQuota(Long operatorUserId, GrantUserTokenQuotaInput input) {
        if (operatorUserId == null) {
            return "未授权";
        }
        SysUserModel operator = sysUserMapper.selectById(operatorUserId);
        if (!isAdminUser(operator)) {
            return "普通用户不可发放额度";
        }
        if (input == null || input.getUserId() == null) {
            return "目标用户不能为空";
        }
        if (input.getGrantTokens() == null || input.getGrantTokens() <= 0L) {
            return "发放额度必须大于 0";
        }
        SysUserModel target = sysUserMapper.selectById(input.getUserId());
        if (target == null) {
            return "用户不存在";
        }
        return userTokenQuotaService.grantTokens(target.getId(), input.getGrantTokens());
    }

    @Override
    public String updateUserTokenQuota(Long operatorUserId, UpdateUserTokenQuotaInput input) {
        if (operatorUserId == null) {
            return "未授权";
        }
        SysUserModel operator = sysUserMapper.selectById(operatorUserId);
        if (!isAdminUser(operator)) {
            return "普通用户不可修改额度";
        }
        if (input == null || input.getUserId() == null) {
            return "目标用户不能为空";
        }
        if (input.getRemainingTokens() == null || input.getRemainingTokens() < 0L) {
            return "剩余额度不能小于 0";
        }
        SysUserModel target = sysUserMapper.selectById(input.getUserId());
        if (target == null) {
            return "用户不存在";
        }
        return userTokenQuotaService.updateQuota(target.getId(), input.getRemainingTokens(), input.getUnlimited());
    }

    private static String normalizeText(String value) {
        return StringUtils.trimToEmpty(value);
    }

    private static String validateUserForm(String name, String account, String password) {
        if (StringUtils.isBlank(name)) {
            return "姓名不能为空";
        }
        if (StringUtils.isBlank(account)) {
            return "登录名不能为空";
        }
        if (StringUtils.isBlank(password)) {
            return "密码不能为空";
        }
        String passwordValidationError = validatePasswordPolicy(password);
        if (passwordValidationError != null) {
            return passwordValidationError;
        }
        return null;
    }

    private static String validatePasswordPolicy(String password) {
        if (StringUtils.isBlank(password)) {
            return "密码不能为空";
        }
        if (password.length() < 6) {
            return "密码至少6位";
        }

        int categories = 0;
        if (password.matches(".*[A-Za-z].*")) {
            categories += 1;
        }
        if (password.matches(".*\\d.*")) {
            categories += 1;
        }
        if (password.matches(".*[^A-Za-z0-9].*")) {
            categories += 1;
        }
        if (categories < 2) {
            return "密码需至少包含字母、数字、符号中的两种";
        }
        return null;
    }

    private static String validateOptionalContact(String mobile, String email) {
        if (StringUtils.isNotBlank(mobile) && !mobile.matches("^1\\d{10}$")) {
            return "手机号格式不正确";
        }
        if (StringUtils.isNotBlank(email) && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return "邮箱格式不正确";
        }
        return null;
    }

    private static UserPageResult emptyPageResult(int page, int pageSize) {
        UserPageResult result = new UserPageResult();
        result.setItems(List.of());
        result.setTotal(0L);
        result.setPage(page);
        result.setPageSize(pageSize);
        return result;
    }

    private boolean isAccountTakenByOther(String account, Long selfId) {
        SysUserModel existingByCode = sysUserMapper.selectByCode(account);
        if (existingByCode == null) {
            return false;
        }
        return selfId == null || !existingByCode.getId().equals(selfId);
    }

    private static boolean isAdminUser(SysUserModel user) {
        return user != null && user.getUserType() != null && user.getUserType() == UserType.admin.getValue();
    }

    private static boolean isFixedAdmin(SysUserModel user) {
        return user != null && StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(user.getCode()), "admin");
    }

    private static String applyUserForm(
            SysUserModel target,
            String name,
            String account,
            String password,
            String mobile,
            String email,
            Integer userType) {
        target.setName(name);
        target.setCode(account);
        // 持久化前统一为 md5 32位hex（不带前缀）。
        target.setPassword(formatStoredPassword(password));
        target.setMobile(StringUtils.isBlank(mobile) ? null : mobile);
        target.setEmail(StringUtils.isBlank(email) ? null : email);
        target.setUserType(userType != null ? userType : UserType.user.getValue());
        return null;
    }

    private static String formatStoredPassword(String md5Hex) {
        return StringUtils.trimToEmpty(md5Hex).toLowerCase();
    }

    private static String decodeIncomingPassword(String payload, boolean allowPlaintextFallback) {
        if (StringUtils.isBlank(payload)) {
            return "";
        }
        if (payload.matches("^[a-fA-F0-9]{32}$")) {
            return payload.toLowerCase();
        }
        // allowPlaintextFallback=false 时，明文或非法密文都会被拒绝。
        return allowPlaintextFallback ? payload : null;
    }

    private UserListItemDto toUserListItem(SysUserModel user) {
        UserListItemDto dto = new UserListItemDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setCode(user.getCode());
        dto.setMobile(user.getMobile());
        dto.setEmail(user.getEmail());
        if (StringUtils.isNotBlank(user.getAvatarObjectName())) {
            dto.setAvatarUrl(minioService.buildObjectPreviewUrl(user.getAvatarObjectName()));
        }
        dto.setUserType(user.getUserType());
        dto.setState(user.getState());
        // 添加角色信息（处理角色已删除的情况）
        dto.setRoleId(user.getRoleId());
        if (user.getRoleId() != null) {
            String roleName = roleService.getRoleNameById(user.getRoleId());
            dto.setRoleName(roleName != null ? roleName : "(角色已删除)");
        }
        dto.setTokenQuota(userTokenQuotaService.getAdminQuotaSummary(user.getId()));
        return dto;
    }

    @Override
    public void logout(String token) {
        if (StringUtils.isBlank(token)) {
            return;
        }
        tokenBlacklistService.blacklist(token, jwtUtils.getExpirationByToken(token));
    }

    private ApiResponse<SsoExchangeTokenResult> failAndLog(
            SsoExchangeTokenInput input,
            Long matchedUserId,
            String normalizedPhone,
            String status,
            int code,
            String message) {
        saveExchangeLog(input, normalizedPhone, matchedUserId, status, message);
        log.warn(
                "SSO token 交换失败：sourceSystem={}, externalUserId={}, status={}, message={}",
                input == null ? "" : safeTrim(input.getSourceSystem()),
                input == null ? "" : safeTrim(input.getExternalUserId()),
                status,
                message);
        return ApiResponse.fail(code, message);
    }

    private void saveExchangeLog(
            SsoExchangeTokenInput input, String normalizedPhone, Long matchedUserId, String status, String message) {
        if (input == null) {
            return;
        }
        try {
            ExternalTokenExchangeLogModel logRecord = new ExternalTokenExchangeLogModel();
            logRecord.setSourceSystem(safeTrim(input.getSourceSystem()));
            logRecord.setExternalUserId(safeTrim(input.getExternalUserId()));
            logRecord.setExternalPhone(safeTrim(normalizedPhone));
            logRecord.setMatchedUserId(matchedUserId);
            logRecord.setExchangeStatus(status);
            logRecord.setMessage(StringUtils.left(safeTrim(message), 255));
            logRecord.setCreatedAt(new Date());
            externalTokenExchangeLogMapper.insert(logRecord);
        } catch (Exception ex) {
            log.warn(
                    "SSO token 交换日志写入失败：sourceSystem={}, externalUserId={}, status={}, error={}",
                    safeTrim(input.getSourceSystem()),
                    safeTrim(input.getExternalUserId()),
                    status,
                    ex.getMessage());
        }
    }

    private boolean hasMissingSsoFields(SsoExchangeTokenInput input) {
        return StringUtils.isBlank(input.getSourceSystem())
                || StringUtils.isBlank(input.getExternalUserId())
                || StringUtils.isBlank(input.getPhone())
                || input.getTimestamp() == null
                || StringUtils.isBlank(input.getNonce())
                || StringUtils.isBlank(input.getSign());
    }

    private boolean isAllowedSourceSystem(String sourceSystem) {
        Set<String> allowedSourceSystems = Arrays.stream(StringUtils.split(ssoAllowedSourceSystems, ','))
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        return !allowedSourceSystems.isEmpty() && allowedSourceSystems.contains(safeTrim(sourceSystem));
    }

    private boolean isExpiredTimestamp(Long timestamp) {
        if (timestamp == null) {
            return true;
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        return Math.abs(nowSeconds - timestamp) > ssoRequestExpireSeconds;
    }

    private static String buildNonceKey(String sourceSystem, String nonce) {
        return safeTrim(sourceSystem) + ":" + safeTrim(nonce);
    }

    private static String normalizePhone(String phone) {
        String value = safeTrim(phone)
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .replace(".", "");
        if (value.startsWith("+86")) {
            value = value.substring(3);
        }
        if (value.startsWith("86") && value.length() > 11) {
            value = value.substring(2);
        }
        return value.replaceAll("[^0-9]", "");
    }

    private static boolean isValidPhone(String phone) {
        return StringUtils.isNotBlank(phone) && phone.matches("^1\\d{10}$");
    }

    private List<SysUserModel> findUsersByNormalizedMobile(String normalizedPhone) {
        List<SysUserModel> directMatchedUsers = sysUserMapper.selectByMobile(normalizedPhone);
        if (!directMatchedUsers.isEmpty()) {
            return directMatchedUsers;
        }
        return sysUserMapper
                .selectList(new LambdaQueryWrapper<SysUserModel>().isNotNull(SysUserModel::getMobile))
                .stream()
                .filter(user -> StringUtils.equals(normalizedPhone, normalizePhone(user.getMobile())))
                .collect(Collectors.toList());
    }

    private static String safeTrim(String value) {
        return StringUtils.trimToEmpty(value);
    }

    private byte[] readFileBytes(MultipartFile file) throws TaskException {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new TaskException("读取头像文件失败，请重试", TaskException.Code.UNKNOWN, ex);
        }
    }

    private void cleanupPreviousAvatar(String previousObjectName, String currentObjectName, Long userId) {
        if (StringUtils.isBlank(previousObjectName) || StringUtils.equals(previousObjectName, currentObjectName)) {
            return;
        }
        cleanupUploadedAvatar(previousObjectName, userId, "替换头像后清理旧文件");
    }

    private void cleanupUploadedAvatar(String objectName, Long userId, String action) {
        if (StringUtils.isBlank(objectName)) {
            return;
        }
        try {
            minioService.deleteFile(objectName);
        } catch (Exception ex) {
            log.warn("{}失败：userId={}, objectName={}, error={}", action, userId, objectName, ex.getMessage(), ex);
        }
    }

    private static String normalizeAvatarExtension(String fileName) {
        String normalized = StringUtils.trimToEmpty(fileName).toLowerCase();
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(dot);
    }

    private static boolean matchesAvatarExtension(String requestedExtension, ImageSignatureUtils.SupportedImageType imageType) {
        if (imageType == null) {
            return false;
        }
        return switch (requestedExtension) {
            case ".jpg", ".jpeg" -> imageType == ImageSignatureUtils.SupportedImageType.JPEG;
            case ".png" -> imageType == ImageSignatureUtils.SupportedImageType.PNG;
            default -> false;
        };
    }

    private static Exception asException(Exception ex) {
        return ex;
    }
}
