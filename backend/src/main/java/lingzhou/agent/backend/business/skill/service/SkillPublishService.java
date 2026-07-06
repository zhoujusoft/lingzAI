package lingzhou.agent.backend.business.skill.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import lingzhou.agent.backend.business.skill.domain.SkillCatalog;
import lingzhou.agent.backend.business.skill.domain.SkillPublishBinding;
import lingzhou.agent.backend.business.skill.mapper.SkillCatalogMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillPublishBindingMapper;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SkillPublishService {

    public static final long CHATBOT_DEFAULT_USER_ID = 90000001L;
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String PASSPORT_SECRET = "skill-chatbot-passport-v1";
    private static final int MAX_APP_NAME_LENGTH = 255;
    private static final int APP_CODE_LENGTH = 16;

    private final SkillPublishBindingMapper skillPublishBindingMapper;
    private final SkillCatalogMapper skillCatalogMapper;
    private final SkillPythonEnvAdminService skillPythonEnvAdminService;

    public SkillPublishService(
            SkillPublishBindingMapper skillPublishBindingMapper,
            SkillCatalogMapper skillCatalogMapper,
            SkillPythonEnvAdminService skillPythonEnvAdminService) {
        this.skillPublishBindingMapper = skillPublishBindingMapper;
        this.skillCatalogMapper = skillCatalogMapper;
        this.skillPythonEnvAdminService = skillPythonEnvAdminService;
    }

    public PublishStatusView getPublishStatus(Long skillId) throws TaskException {
        SkillCatalog catalog = requireSkillCatalog(skillId);
        SkillPublishBinding binding = skillPublishBindingMapper.selectBySkillId(skillId);
        return toView(catalog, binding, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PublishStatusView publish(Long skillId, String appName, String appDescription) throws TaskException {
        SkillCatalog catalog = requireSkillCatalog(skillId);
        SkillPublishBinding binding = skillPublishBindingMapper.selectBySkillId(skillId);
        if (binding == null) {
            binding = new SkillPublishBinding();
            binding.setSkillId(skillId);
            binding.setAppCode(nextUniqueAppCode());
        }
        binding.setAppName(normalizeNullableText(appName, MAX_APP_NAME_LENGTH));
        binding.setAppDescription(normalizeNullableText(appDescription, 2000));
        binding.setPublishStatus(STATUS_PUBLISHED);
        binding.setPublishedAt(new Date());
        saveBinding(binding);
        PythonEnvWarmupView pythonEnvWarmup = prewarmPythonEnv(skillId);
        return toView(catalog, binding, pythonEnvWarmup);
    }

    @Transactional(rollbackFor = Exception.class)
    public PublishStatusView updatePublishedInfo(Long skillId, String appName, String appDescription)
            throws TaskException {
        SkillCatalog catalog = requireSkillCatalog(skillId);
        SkillPublishBinding binding = skillPublishBindingMapper.selectBySkillId(skillId);
        if (binding == null || !STATUS_PUBLISHED.equalsIgnoreCase(binding.getPublishStatus())) {
            throw new TaskException("当前技能尚未发布，无法更新发布信息", TaskException.Code.UNKNOWN);
        }
        binding.setAppName(normalizeNullableText(appName, MAX_APP_NAME_LENGTH));
        binding.setAppDescription(normalizeNullableText(appDescription, 2000));
        skillPublishBindingMapper.updateById(binding);
        return toView(catalog, binding, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PublishStatusView disable(Long skillId) throws TaskException {
        SkillCatalog catalog = requireSkillCatalog(skillId);
        SkillPublishBinding binding = skillPublishBindingMapper.selectBySkillId(skillId);
        if (binding == null) {
            binding = new SkillPublishBinding();
            binding.setSkillId(skillId);
            binding.setAppCode(nextUniqueAppCode());
        }
        binding.setPublishStatus(STATUS_DISABLED);
        saveBinding(binding);
        return toView(catalog, binding, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PublishStatusView regenerateAppCode(Long skillId) throws TaskException {
        SkillCatalog catalog = requireSkillCatalog(skillId);
        SkillPublishBinding binding = skillPublishBindingMapper.selectBySkillId(skillId);
        if (binding == null || !STATUS_PUBLISHED.equalsIgnoreCase(binding.getPublishStatus())) {
            throw new TaskException("当前技能尚未发布，无法重新生成地址", TaskException.Code.UNKNOWN);
        }
        binding.setAppCode(nextUniqueAppCode());
        binding.setPublishedAt(new Date());
        skillPublishBindingMapper.updateById(binding);
        return toView(catalog, binding, null);
    }

    public PublishedSkillContext resolvePublishedSkillContext(String appCode) throws TaskException {
        String normalizedCode = normalizeAppCode(appCode);
        SkillPublishBinding binding = skillPublishBindingMapper.selectByAppCode(normalizedCode);
        if (binding == null || !STATUS_PUBLISHED.equalsIgnoreCase(binding.getPublishStatus())) {
            throw new TaskException("当前应用未发布或地址无效", TaskException.Code.UNKNOWN);
        }
        SkillCatalog catalog = requireSkillCatalog(binding.getSkillId());
        String appName = firstNonBlank(binding.getAppName(), catalog.getDisplayName());
        String appDescription = firstNonBlank(binding.getAppDescription(), catalog.getDescription());
        return new PublishedSkillContext(
                normalizedCode, catalog.getId(), catalog.getRuntimeSkillName(), appName, appDescription);
    }

    public PublishAccessStatusView getPublishAccessStatus(String appCode) throws TaskException {
        String normalizedCode = normalizeAppCode(appCode);
        SkillPublishBinding binding = skillPublishBindingMapper.selectByAppCode(normalizedCode);
        if (binding == null) {
            return new PublishAccessStatusView(normalizedCode, false, false, false, "APP_NOT_FOUND");
        }
        SkillCatalog catalog = skillCatalogMapper.selectById(binding.getSkillId());
        if (catalog == null) {
            return new PublishAccessStatusView(normalizedCode, true, false, false, "SKILL_NOT_FOUND");
        }
        boolean published = STATUS_PUBLISHED.equalsIgnoreCase(binding.getPublishStatus());
        return new PublishAccessStatusView(
                normalizedCode, true, true, published, published ? "PUBLISHED" : "NOT_PUBLISHED");
    }

    public PassportIssueResult issuePassport(String appCode, Long userId) throws TaskException {
        PublishedSkillContext context = resolvePublishedSkillContext(appCode);
        long normalizedUserId = normalizePassportUserId(userId);
        String signature = signPassport(context.appCode(), normalizedUserId);
        String passport = context.appCode() + "." + normalizedUserId + "." + signature;
        return new PassportIssueResult(context.appCode(), normalizedUserId, passport);
    }

    public PassportClaims validatePassport(String appCode, String passport) throws TaskException {
        String normalizedAppCode = normalizeAppCode(appCode);
        PublishedSkillContext context = resolvePublishedSkillContext(normalizedAppCode);
        String normalized = StringUtils.hasText(passport) ? passport.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            throw new TaskException("X-App-Passport 不能为空", TaskException.Code.UNKNOWN);
        }
        String[] parts = normalized.split("\\.");
        if (parts.length != 3) {
            throw new TaskException("X-App-Passport 格式无效", TaskException.Code.UNKNOWN);
        }
        String passportAppCode = StringUtils.hasText(parts[0]) ? parts[0].trim().toLowerCase(Locale.ROOT) : "";
        if (!context.appCode().equals(passportAppCode)) {
            throw new TaskException("X-App-Code 与 X-App-Passport 不匹配", TaskException.Code.UNKNOWN);
        }
        long passportUserId;
        try {
            passportUserId = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            throw new TaskException("X-App-Passport 中的 userId 无效", TaskException.Code.UNKNOWN);
        }
        String expectedSignature = signPassport(context.appCode(), passportUserId);
        String actualSignature = StringUtils.hasText(parts[2]) ? parts[2].trim().toLowerCase(Locale.ROOT) : "";
        if (!expectedSignature.equals(actualSignature)) {
            throw new TaskException("X-App-Passport 无效", TaskException.Code.UNKNOWN);
        }
        return new PassportClaims(context.appCode(), passportUserId);
    }

    private SkillCatalog requireSkillCatalog(Long skillId) throws TaskException {
        if (skillId == null || skillId <= 0) {
            throw new TaskException("技能ID无效", TaskException.Code.UNKNOWN);
        }
        SkillCatalog catalog = skillCatalogMapper.selectById(skillId);
        if (catalog == null) {
            throw new TaskException("技能不存在", TaskException.Code.UNKNOWN);
        }
        return catalog;
    }

    private void saveBinding(SkillPublishBinding binding) {
        if (binding.getId() == null) {
            skillPublishBindingMapper.insert(binding);
        } else {
            skillPublishBindingMapper.updateById(binding);
        }
    }

    private PublishStatusView toView(
            SkillCatalog catalog, SkillPublishBinding binding, PythonEnvWarmupView pythonEnvWarmup) {
        String publishStatus = binding == null || !StringUtils.hasText(binding.getPublishStatus())
                ? STATUS_DISABLED
                : binding.getPublishStatus().trim().toUpperCase(Locale.ROOT);
        String appCode = binding == null ? "" : normalizeNullableText(binding.getAppCode(), 32);
        String appName = firstNonBlank(binding == null ? null : binding.getAppName(), catalog.getDisplayName());
        String appDescription =
                firstNonBlank(binding == null ? null : binding.getAppDescription(), catalog.getDescription());
        String url =
                StringUtils.hasText(appCode) && STATUS_PUBLISHED.equals(publishStatus) ? "/chatbot/" + appCode : "";
        return new PublishStatusView(
                catalog.getId(),
                publishStatus,
                appCode,
                appName,
                appDescription,
                binding == null ? null : binding.getPublishedAt(),
                url,
                pythonEnvWarmup);
    }

    private PythonEnvWarmupView prewarmPythonEnv(Long skillId) {
        try {
            SkillPythonEnvAdminService.SkillPythonEnvView envView =
                    skillPythonEnvAdminService.prewarmForPublish(skillId);
            String installStatus = envView.manifest() == null
                    ? ""
                    : normalizeNullableText(envView.manifest().installStatus(), 32);
            return new PythonEnvWarmupView(
                    true,
                    true,
                    "",
                    envView.dedicated(),
                    envView.envRoot(),
                    envView.venvPath(),
                    envView.pythonPath(),
                    envView.reusable(),
                    installStatus,
                    envView.installLogPath());
        } catch (Exception ex) {
            return new PythonEnvWarmupView(
                    true,
                    false,
                    ex.getMessage() == null ? "Python 环境预热失败" : ex.getMessage(),
                    false,
                    "",
                    "",
                    "",
                    false,
                    "FAILED",
                    "");
        }
    }

    private String nextUniqueAppCode() {
        for (int i = 0; i < 10; i++) {
            String candidate = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, APP_CODE_LENGTH)
                    .toLowerCase(Locale.ROOT);
            if (skillPublishBindingMapper.selectByAppCode(candidate) == null) {
                return candidate;
            }
        }
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, APP_CODE_LENGTH)
                .toLowerCase(Locale.ROOT);
    }

    private String signPassport(String appCode, long userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String source = appCode + "|" + userId + "|" + PASSPORT_SECRET;
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("passport sign failed", ex);
        }
    }

    private long normalizePassportUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return CHATBOT_DEFAULT_USER_ID;
        }
        return userId;
    }

    private String normalizeAppCode(String appCode) throws TaskException {
        String normalized = normalizeNullableText(appCode, 32);
        if (!StringUtils.hasText(normalized)) {
            throw new TaskException("AppCode 不能为空", TaskException.Code.UNKNOWN);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeNullableText(String value, int maxLength) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : "";
    }

    public record PublishStatusView(
            Long skillId,
            String publishStatus,
            String appCode,
            String appName,
            String appDescription,
            Date publishedAt,
            String chatbotUrl,
            PythonEnvWarmupView pythonEnvWarmup) {}

    public record PythonEnvWarmupView(
            boolean triggered,
            boolean success,
            String message,
            boolean dedicated,
            String envRoot,
            String venvPath,
            String pythonPath,
            boolean reusable,
            String installStatus,
            String installLogPath) {}

    public record PublishedSkillContext(
            String appCode, Long skillId, String runtimeSkillName, String displayName, String description) {}

    public record PublishAccessStatusView(
            String appCode, boolean appCodeExists, boolean skillExists, boolean published, String reason) {}

    public record PassportIssueResult(String appCode, long userId, String passport) {}

    public record PassportClaims(String appCode, long userId) {}
}
