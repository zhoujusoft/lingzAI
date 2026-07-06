package lingzhou.agent.backend.business.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lingzhou.agent.backend.business.system.dao.SysRoleMapper;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.model.CreateUserInput;
import lingzhou.agent.backend.business.system.model.SysRole;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.system.service.RegisterUserSkillService;
import lingzhou.agent.backend.business.system.service.RoleService;
import lingzhou.agent.backend.business.system.service.UserService;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import lingzhou.agent.backend.common.security.MD5Encryptor;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserSkillServiceImpl implements RegisterUserSkillService {

    private static final String DEFAULT_PASSWORD = "zhouju123.123";
    private static final String DEFAULT_ROLE_CODE = "manage-user";
    private static final String PLATFORM_URL = "http://lingz.zhoujusoft.com/";
    private static final int MAX_USERNAME_LENGTH = 64;
    private static final Set<String> COMPOUND_SURNAMES = Set.of(
            "欧阳", "司马", "上官", "诸葛", "东方", "夏侯", "皇甫", "尉迟", "公孙", "长孙", "宇文", "司徒", "司空", "独孤", "南宫", "闻人", "轩辕", "赫连",
            "澹台", "闾丘");

    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final RoleService roleService;
    private final UserService userService;

    public RegisterUserSkillServiceImpl(
            SysRoleMapper sysRoleMapper,
            SysUserMapper sysUserMapper,
            RoleService roleService,
            UserService userService) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserMapper = sysUserMapper;
        this.roleService = roleService;
        this.userService = userService;
    }

    @Override
    public PreviewResult preview(PreviewCommand command) throws TaskException {
        NormalizedPayload payload = normalizePayload(
                command == null ? null : command.username(),
                command == null ? null : command.name(),
                command == null ? null : command.password(),
                command == null ? null : command.mobile(),
                command == null ? null : command.email(),
                false);
        return new PreviewResult(
                payload.username(),
                payload.name(),
                payload.password(),
                payload.defaultPassword(),
                payload.mobile(),
                payload.email(),
                payload.generatedUsername(),
                List.copyOf(payload.notices()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConfirmResult confirm(Long operatorUserId, ConfirmCommand command) throws TaskException {
        if (command == null || !Boolean.TRUE.equals(command.confirm())) {
            throw new TaskException("未确认创建，不能执行创建", TaskException.Code.UNKNOWN);
        }
        NormalizedPayload payload = normalizePayload(
                command.username(), command.name(), command.password(), command.mobile(), command.email(), true);
        if (isAccountTaken(payload.username())) {
            throw new TaskException("登录名已存在", TaskException.Code.UNKNOWN);
        }
        CreateUserInput input = new CreateUserInput();
        input.setAccount(payload.username());
        input.setName(payload.name());
        input.setPassword(md5Hex(payload.password()));
        input.setMobile(payload.mobile());
        input.setEmail(payload.email());
        input.setUserType(UserType.user.getValue());
        input.setRoleId(resolveDefaultRoleId());
        String errorMessage = userService.createUserWithoutPermissionCheck(input);
        if (StringUtils.isNotBlank(errorMessage)) {
            throw new TaskException(errorMessage.trim(), TaskException.Code.UNKNOWN);
        }
        SysUserModel user = sysUserMapper.selectByCode(payload.username());
        if (user == null || user.getId() == null) {
            throw new TaskException("新增用户失败", TaskException.Code.UNKNOWN);
        }
        return new ConfirmResult(
                user.getId(),
                payload.username(),
                payload.name(),
                DEFAULT_ROLE_CODE,
                payload.defaultPassword(),
                payload.mobile(),
                payload.email(),
                PLATFORM_URL,
                "创建成功");
    }

    private NormalizedPayload normalizePayload(
            String rawUsername,
            String rawName,
            String rawPassword,
            String rawMobile,
            String rawEmail,
            boolean strictConfirm)
            throws TaskException {
        String username = normalize(rawUsername);
        String name = normalize(rawName);
        String mobile = blankToNull(normalize(rawMobile));
        String email = blankToNull(normalize(rawEmail));
        if (StringUtils.isBlank(username) && StringUtils.isBlank(name)) {
            throw new TaskException("用户名和姓名至少提供一个", TaskException.Code.UNKNOWN);
        }
        List<String> notices = new ArrayList<>();
        boolean generatedUsername = false;
        if (StringUtils.isBlank(username)) {
            generatedUsername = true;
            String generated = generateUsernameFromName(name);
            if (StringUtils.isBlank(generated)) {
                throw new TaskException("无法根据姓名稳定生成用户名，请手动提供用户名", TaskException.Code.UNKNOWN);
            }
            username = ensureUniqueAutoUsername(generated);
            notices.add("已根据姓名自动生成用户名：" + username);
        } else {
            username = sanitizeUsername(username);
            if (StringUtils.isBlank(username)) {
                throw new TaskException("用户名不能为空", TaskException.Code.UNKNOWN);
            }
            if (strictConfirm && isAccountTaken(username)) {
                throw new TaskException("登录名已存在", TaskException.Code.UNKNOWN);
            }
        }
        if (StringUtils.isBlank(name)) {
            name = username;
            notices.add("未提供姓名，已默认使用用户名作为姓名");
        }
        boolean defaultPassword = StringUtils.isBlank(rawPassword);
        String password = defaultPassword ? DEFAULT_PASSWORD : normalize(rawPassword);
        validatePlainPassword(password);
        if (!defaultPassword) {
            notices.add("已使用自定义密码");
        }
        return new NormalizedPayload(
                username, name, password, defaultPassword, mobile, email, generatedUsername, notices);
    }

    private boolean isAccountTaken(String account) {
        if (StringUtils.isBlank(account)) {
            return false;
        }
        return sysUserMapper.selectByCode(account) != null;
    }

    private Long resolveDefaultRoleId() throws TaskException {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, DEFAULT_ROLE_CODE)
                .last("LIMIT 1"));
        if (role == null || role.getId() == null) {
            throw new TaskException("默认角色不存在，请先配置角色编码：" + DEFAULT_ROLE_CODE, TaskException.Code.UNKNOWN);
        }
        String roleError = roleService.checkRoleUsable(role.getId());
        if (StringUtils.isNotBlank(roleError)) {
            throw new TaskException("默认角色不可用：" + roleError.trim(), TaskException.Code.UNKNOWN);
        }
        return role.getId();
    }

    private String ensureUniqueAutoUsername(String base) {
        String normalizedBase = truncate(base);
        if (!isAccountTaken(normalizedBase)) {
            return normalizedBase;
        }
        for (int index = 1; index < 10000; index++) {
            String suffix = String.valueOf(index);
            String candidate = truncate(normalizedBase, suffix.length()) + suffix;
            if (!isAccountTaken(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法生成唯一用户名");
    }

    private String generateUsernameFromName(String name) {
        String normalizedName = normalize(name);
        if (StringUtils.isBlank(normalizedName)) {
            return "";
        }
        if (containsChinese(normalizedName)) {
            String generated = generateChineseUsername(normalizedName);
            if (StringUtils.isNotBlank(generated)) {
                return sanitizeUsername(generated);
            }
        }
        String fallback = normalizedName
                .replace("·", "")
                .replace(".", "")
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
        fallback = fallback.replaceAll("[^a-z0-9]", "");
        return sanitizeUsername(fallback);
    }

    private String generateChineseUsername(String name) {
        List<String> chars = splitChineseCharacters(name);
        if (chars.isEmpty()) {
            return "";
        }
        int surnameLength = resolveSurnameLength(chars);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < surnameLength && i < chars.size(); i++) {
            builder.append(toPinyin(chars.get(i), false));
        }
        for (int i = surnameLength; i < chars.size(); i++) {
            String initial = toPinyin(chars.get(i), true);
            if (StringUtils.isBlank(initial)) {
                return "";
            }
            builder.append(initial);
        }
        return builder.toString();
    }

    private List<String> splitChineseCharacters(String value) {
        List<String> chars = new ArrayList<>();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isWhitespace(current) || current == '·' || current == '.' || current == '-') {
                continue;
            }
            chars.add(String.valueOf(current));
        }
        return chars;
    }

    private int resolveSurnameLength(List<String> chars) {
        if (chars.size() < 2) {
            return 1;
        }
        String firstTwo = chars.get(0) + chars.get(1);
        return COMPOUND_SURNAMES.contains(firstTwo) ? 2 : 1;
    }

    private String toPinyin(String chinese, boolean firstLetterOnly) {
        try {
            String pinyin = PinyinHelper.toPinyin(chinese, PinyinStyleEnum.NORMAL);
            if (StringUtils.isBlank(pinyin)) {
                return "";
            }
            String normalized = pinyin.replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.ROOT);
            if (StringUtils.isBlank(normalized)) {
                return "";
            }
            return firstLetterOnly ? normalized.substring(0, 1) : normalized;
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean containsChinese(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private void validatePlainPassword(String password) throws TaskException {
        if (StringUtils.isBlank(password)) {
            throw new TaskException("密码不能为空", TaskException.Code.UNKNOWN);
        }
        if (password.length() < 6) {
            throw new TaskException("密码至少6位", TaskException.Code.UNKNOWN);
        }
        int categories = 0;
        if (password.matches(".*[A-Za-z].*")) {
            categories++;
        }
        if (password.matches(".*\\d.*")) {
            categories++;
        }
        if (password.matches(".*[^A-Za-z0-9].*")) {
            categories++;
        }
        if (categories < 2) {
            throw new TaskException("密码需至少包含字母、数字、符号中的两种", TaskException.Code.UNKNOWN);
        }
    }

    private String md5Hex(String plainPassword) throws TaskException {
        try {
            return MD5Encryptor.getMD5(plainPassword);
        } catch (Exception ex) {
            throw new TaskException("密码加密失败", TaskException.Code.UNKNOWN);
        }
    }

    private String sanitizeUsername(String username) {
        String normalized = normalize(username).toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]", "");
        return truncate(normalized);
    }

    private String truncate(String value) {
        return truncate(value, 0);
    }

    private String truncate(String value, int reservedSuffixLength) {
        String normalized = normalize(value);
        if (normalized.length() <= MAX_USERNAME_LENGTH - reservedSuffixLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(1, MAX_USERNAME_LENGTH - reservedSuffixLength));
    }

    private String normalize(String value) {
        return StringUtils.trimToEmpty(value);
    }

    private String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    private record NormalizedPayload(
            String username,
            String name,
            String password,
            boolean defaultPassword,
            String mobile,
            String email,
            boolean generatedUsername,
            List<String> notices) {}
}
