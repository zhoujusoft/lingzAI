package lingzhou.agent.backend.business.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.dao.SystemConfigMapper;
import lingzhou.agent.backend.business.system.model.BrandingLogoUploadResult;
import lingzhou.agent.backend.business.system.model.BrandingSettingsDto;
import lingzhou.agent.backend.business.system.model.PlatformAuthConfig;
import lingzhou.agent.backend.business.system.model.PlatformEndpointItem;
import lingzhou.agent.backend.business.system.model.PlatformSettingsDto;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.system.model.SystemConfigModel;
import lingzhou.agent.backend.business.system.model.TokenQuotaSettingsDto;
import lingzhou.agent.backend.business.system.model.UpdateBrandingSettingsInput;
import lingzhou.agent.backend.business.system.model.UpdatePlatformSettingsInput;
import lingzhou.agent.backend.business.system.model.UpdateTokenQuotaSettingsInput;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SystemConfigService {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigService.class);
    private static final String CONFIG_KEY_SELF_HOSTED_PLATFORMS = "self_hosted_platforms";
    private static final String CONFIG_KEY_TOKEN_QUOTA_SETTINGS = "token_quota_settings";
    private static final String CONFIG_KEY_UI_BRANDING_SETTINGS = "ui_branding_settings";
    private static final String AUTH_TYPE_NONE = "NONE";
    private static final String AUTH_TYPE_SYSTEM_TOKEN = "SYSTEM_TOKEN";
    private static final Pattern PLATFORM_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final long DEFAULT_INITIAL_GRANT_TOKENS = 1_000_000L;
    private static final String DEFAULT_SYSTEM_NAME = "灵洲AI平台";
    private static final String DEFAULT_LOGO_URL = "/logo.png";
    private static final int MAX_SYSTEM_NAME_LENGTH = 32;
    private static final long MAX_BRANDING_LOGO_SIZE = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_BRANDING_LOGO_EXTENSIONS =
            Set.of(".png", ".jpg", ".jpeg", ".webp", ".svg", ".ico");
    private static final ObjectMapper JSON =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final SystemConfigMapper systemConfigMapper;
    private final SysUserMapper sysUserMapper;
    private final MinioService minioService;

    public SystemConfigService(
            SystemConfigMapper systemConfigMapper, SysUserMapper sysUserMapper, MinioService minioService) {
        this.systemConfigMapper = systemConfigMapper;
        this.sysUserMapper = sysUserMapper;
        this.minioService = minioService;
    }

    public PlatformSettingsDto getPlatformSettings(Long operatorUserId) throws TaskException {
        requireAdmin(operatorUserId);
        return buildPlatformSettingsDto(systemConfigMapper.selectByConfigKey(CONFIG_KEY_SELF_HOSTED_PLATFORMS), true);
    }

    public List<PlatformEndpointItem> getEnabledPlatformItems() {
        SystemConfigModel config = systemConfigMapper.selectByConfigKey(CONFIG_KEY_SELF_HOSTED_PLATFORMS);
        if (config == null || config.getStatus() == null || config.getStatus() != 1) {
            return List.of();
        }
        return parsePlatformItems(config.getConfigValue(), false).stream()
                .filter(item -> item.getStatus() != null && item.getStatus() == 1)
                .toList();
    }

    public PlatformEndpointItem getEnabledPlatformByKey(String platformKey) throws TaskException {
        if (StringUtils.isBlank(platformKey)) {
            throw new TaskException("平台标识不能为空", TaskException.Code.UNKNOWN);
        }
        return getEnabledPlatformItems().stream()
                .filter(item -> platformKey.trim().equals(item.getKey()))
                .findFirst()
                .orElseThrow(() -> new TaskException("平台不存在或未启用：" + platformKey, TaskException.Code.UNKNOWN));
    }

    public TokenQuotaSettingsDto getTokenQuotaSettings(Long operatorUserId) throws TaskException {
        requireAdmin(operatorUserId);
        return buildTokenQuotaSettingsDto(systemConfigMapper.selectByConfigKey(CONFIG_KEY_TOKEN_QUOTA_SETTINGS));
    }

    public TokenQuotaSettingsDto getTokenQuotaSettingsSnapshot() {
        return buildTokenQuotaSettingsDto(systemConfigMapper.selectByConfigKey(CONFIG_KEY_TOKEN_QUOTA_SETTINGS));
    }

    public BrandingSettingsDto getBrandingSettings() {
        return buildBrandingSettingsDto(systemConfigMapper.selectByConfigKey(CONFIG_KEY_UI_BRANDING_SETTINGS));
    }

    @Transactional(rollbackFor = Exception.class)
    public BrandingSettingsDto saveBrandingSettings(Long operatorUserId, UpdateBrandingSettingsInput input)
            throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        if (input == null) {
            throw new TaskException("请求参数不能为空", TaskException.Code.UNKNOWN);
        }

        SystemConfigModel config = systemConfigMapper.selectByConfigKey(CONFIG_KEY_UI_BRANDING_SETTINGS);
        boolean creating = config == null;
        if (config == null) {
            config = new SystemConfigModel();
            config.setConfigKey(CONFIG_KEY_UI_BRANDING_SETTINGS);
        }

        String normalizedSystemName = normalizeSystemName(input.getSystemName());
        String normalizedLogoObjectName = normalizeLogoObjectName(input.getLogoObjectName());
        config.setStatus(1);
        config.setConfigValue(serializeBrandingSettings(normalizedSystemName, normalizedLogoObjectName));

        int affectedRows = creating ? systemConfigMapper.insert(config) : systemConfigMapper.updateById(config);
        if (affectedRows <= 0) {
            throw new TaskException("保存品牌配置失败", TaskException.Code.UNKNOWN);
        }

        logger.info(
                "System config updated: configKey={}, operatorUserId={}, created={}",
                CONFIG_KEY_UI_BRANDING_SETTINGS,
                operator.getId(),
                creating);
        return buildBrandingSettingsDto(systemConfigMapper.selectByConfigKey(CONFIG_KEY_UI_BRANDING_SETTINGS));
    }

    public BrandingLogoUploadResult uploadBrandingLogo(Long operatorUserId, MultipartFile file) throws TaskException {
        requireAdmin(operatorUserId);
        if (file == null || file.isEmpty()) {
            throw new TaskException("请先选择图标文件", TaskException.Code.UNKNOWN);
        }
        if (file.getSize() > MAX_BRANDING_LOGO_SIZE) {
            throw new TaskException("图标文件不能超过 2MB", TaskException.Code.UNKNOWN);
        }

        String originalName = StringUtils.trimToEmpty(file.getOriginalFilename());
        String extension = normalizeBrandingLogoExtension(originalName);
        String uploadFileName = "logo" + extension;
        try {
            MinioService.ArtifactUploadResult uploadResult =
                    minioService.uploadArtifact("branding", uploadFileName, file.getBytes(), file.getContentType());
            BrandingLogoUploadResult result = new BrandingLogoUploadResult();
            result.setLogoObjectName(uploadResult.objectName());
            result.setLogoUrl(minioService.buildArtifactPreviewUrl(uploadResult.objectName(), uploadFileName));
            result.setFaviconUrl(result.getLogoUrl());
            return result;
        } catch (Exception ex) {
            logger.warn("Branding logo upload failed: fileName={}, error={}", originalName, ex.getMessage(), ex);
            throw new TaskException("图标上传失败，请稍后重试", TaskException.Code.UNKNOWN, ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public TokenQuotaSettingsDto saveTokenQuotaSettings(Long operatorUserId, UpdateTokenQuotaSettingsInput input)
            throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        if (input == null) {
            throw new TaskException("请求参数不能为空", TaskException.Code.UNKNOWN);
        }
        SystemConfigModel config = systemConfigMapper.selectByConfigKey(CONFIG_KEY_TOKEN_QUOTA_SETTINGS);
        boolean creating = config == null;
        if (config == null) {
            config = new SystemConfigModel();
            config.setConfigKey(CONFIG_KEY_TOKEN_QUOTA_SETTINGS);
        }
        config.setStatus(normalizeStatus(input.getStatus(), "配置状态仅支持启用或停用"));
        config.setConfigValue(serializeTokenQuotaSettings(input.getInitialGrantTokens()));
        int affectedRows = creating ? systemConfigMapper.insert(config) : systemConfigMapper.updateById(config);
        if (affectedRows <= 0) {
            throw new TaskException("保存 token 配置失败", TaskException.Code.UNKNOWN);
        }
        logger.info(
                "System config updated: configKey={}, operatorUserId={}, created={}",
                CONFIG_KEY_TOKEN_QUOTA_SETTINGS,
                operator.getId(),
                creating);
        return buildTokenQuotaSettingsDto(systemConfigMapper.selectByConfigKey(CONFIG_KEY_TOKEN_QUOTA_SETTINGS));
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatformSettingsDto savePlatformSettings(Long operatorUserId, UpdatePlatformSettingsInput input)
            throws TaskException {
        SysUserModel operator = requireAdmin(operatorUserId);
        if (input == null) {
            throw new TaskException("请求参数不能为空", TaskException.Code.UNKNOWN);
        }

        SystemConfigModel config = systemConfigMapper.selectByConfigKey(CONFIG_KEY_SELF_HOSTED_PLATFORMS);
        boolean creating = config == null;
        if (config == null) {
            config = new SystemConfigModel();
            config.setConfigKey(CONFIG_KEY_SELF_HOSTED_PLATFORMS);
        }

        List<PlatformEndpointItem> existingItems = parsePlatformItems(config.getConfigValue(), false);
        config.setConfigValue(serializePlatformItems(input.getPlatforms(), existingItems));
        config.setStatus(normalizeStatus(input.getStatus(), "配置状态仅支持启用或停用"));

        int affectedRows = creating ? systemConfigMapper.insert(config) : systemConfigMapper.updateById(config);
        if (affectedRows <= 0) {
            throw new TaskException("保存平台配置失败", TaskException.Code.UNKNOWN);
        }

        logger.info(
                "System config updated: configKey={}, operatorUserId={}, created={}",
                CONFIG_KEY_SELF_HOSTED_PLATFORMS,
                operator.getId(),
                creating);
        return buildPlatformSettingsDto(systemConfigMapper.selectByConfigKey(CONFIG_KEY_SELF_HOSTED_PLATFORMS), true);
    }

    private SysUserModel requireAdmin(Long operatorUserId) throws TaskException {
        if (operatorUserId == null) {
            throw new TaskException("未授权", TaskException.Code.UNKNOWN);
        }
        SysUserModel operator = sysUserMapper.selectById(operatorUserId);
        if (operator == null) {
            throw new TaskException("未授权", TaskException.Code.UNKNOWN);
        }
        if (!isAdminUser(operator)) {
            throw new TaskException("普通用户不可管理配置", TaskException.Code.UNKNOWN);
        }
        return operator;
    }

    private PlatformSettingsDto buildPlatformSettingsDto(SystemConfigModel config, boolean maskSecrets) {
        PlatformSettingsDto dto = new PlatformSettingsDto();
        dto.setConfigKey(CONFIG_KEY_SELF_HOSTED_PLATFORMS);
        if (config == null) {
            dto.setStatus(1);
            dto.setPlatforms(List.of());
            dto.setUpdatedAt(null);
            return dto;
        }
        dto.setStatus(config.getStatus() == null ? 1 : config.getStatus());
        dto.setPlatforms(parsePlatformItems(config.getConfigValue(), maskSecrets));
        dto.setUpdatedAt(config.getUpdatedAt());
        return dto;
    }

    private TokenQuotaSettingsDto buildTokenQuotaSettingsDto(SystemConfigModel config) {
        TokenQuotaSettingsDto dto = new TokenQuotaSettingsDto();
        dto.setConfigKey(CONFIG_KEY_TOKEN_QUOTA_SETTINGS);
        if (config == null) {
            dto.setStatus(0);
            dto.setEnabled(false);
            dto.setInitialGrantTokens(DEFAULT_INITIAL_GRANT_TOKENS);
            return dto;
        }
        dto.setStatus(config.getStatus() == null ? 0 : config.getStatus());
        dto.setEnabled(dto.getStatus() == 1);
        dto.setInitialGrantTokens(parseInitialGrantTokens(config.getConfigValue()));
        dto.setUpdatedAt(config.getUpdatedAt());
        return dto;
    }

    private BrandingSettingsDto buildBrandingSettingsDto(SystemConfigModel config) {
        BrandingSettingsDto dto = new BrandingSettingsDto();
        dto.setConfigKey(CONFIG_KEY_UI_BRANDING_SETTINGS);
        dto.setStatus(1);
        dto.setSystemName(DEFAULT_SYSTEM_NAME);
        dto.setLogoObjectName("");
        dto.setLogoUrl(DEFAULT_LOGO_URL);
        dto.setFaviconUrl(DEFAULT_LOGO_URL);

        if (config == null) {
            return dto;
        }

        dto.setStatus(config.getStatus() == null ? 1 : config.getStatus());
        dto.setUpdatedAt(config.getUpdatedAt());
        if (StringUtils.isBlank(config.getConfigValue())) {
            return dto;
        }

        try {
            Map<String, Object> payload =
                    JSON.readValue(config.getConfigValue(), new TypeReference<Map<String, Object>>() {});
            String systemName = normalizeSystemName(asText(payload.get("systemName")));
            String logoObjectName = normalizeLogoObjectName(asText(payload.get("logoObjectName")));
            dto.setSystemName(systemName);
            dto.setLogoObjectName(logoObjectName);
            if (StringUtils.isNotBlank(logoObjectName)) {
                String previewFileName = "logo" + extractExtensionFromObjectName(logoObjectName);
                String previewUrl = minioService.buildArtifactPreviewUrl(logoObjectName, previewFileName);
                dto.setLogoUrl(previewUrl);
                dto.setFaviconUrl(previewUrl);
            }
        } catch (Exception ex) {
            logger.warn("Failed to parse system config: configKey={}", CONFIG_KEY_UI_BRANDING_SETTINGS, ex);
        }
        return dto;
    }

    private String serializeBrandingSettings(String systemName, String logoObjectName) throws TaskException {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("systemName", systemName);
            payload.put("logoObjectName", logoObjectName);
            return JSON.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new TaskException("品牌配置序列化失败", TaskException.Code.UNKNOWN, ex);
        }
    }

    private String serializeTokenQuotaSettings(Long initialGrantTokens) throws TaskException {
        long normalizedTokens = normalizeInitialGrantTokens(initialGrantTokens);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("initialGrantTokens", normalizedTokens);
            return JSON.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new TaskException("token 配置序列化失败", TaskException.Code.UNKNOWN, ex);
        }
    }

    private String serializePlatformItems(
            List<PlatformEndpointItem> platforms, List<PlatformEndpointItem> existingItems) throws TaskException {
        Map<String, PlatformEndpointItem> existingByKey = new LinkedHashMap<>();
        if (existingItems != null) {
            for (PlatformEndpointItem item : existingItems) {
                if (item != null && StringUtils.isNotBlank(item.getKey())) {
                    existingByKey.put(item.getKey(), item);
                }
            }
        }

        List<PlatformEndpointItem> normalizedItems = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        if (platforms != null) {
            for (PlatformEndpointItem item : platforms) {
                if (item == null) {
                    continue;
                }
                String key = normalizePlatformKey(item.getKey());
                String name = StringUtils.trimToEmpty(item.getName());
                String apiUrl = StringUtils.trimToEmpty(item.getApiUrl());
                if (StringUtils.isBlank(key)
                        && StringUtils.isBlank(name)
                        && StringUtils.isBlank(apiUrl)
                        && isBlankAuthConfig(item.getAuthConfig())) {
                    continue;
                }
                if (StringUtils.isBlank(key)) {
                    throw new TaskException("平台 key 不能为空", TaskException.Code.UNKNOWN);
                }
                if (!seenKeys.add(key)) {
                    throw new TaskException("平台 key 不可重复：" + key, TaskException.Code.UNKNOWN);
                }
                if (StringUtils.isBlank(name)) {
                    throw new TaskException("平台名称不能为空", TaskException.Code.UNKNOWN);
                }
                if (StringUtils.isBlank(apiUrl)) {
                    throw new TaskException("平台 API URL 不能为空", TaskException.Code.UNKNOWN);
                }

                PlatformEndpointItem normalized = new PlatformEndpointItem();
                normalized.setKey(key);
                normalized.setName(name);
                normalized.setApiUrl(validateApiUrl(apiUrl));
                PlatformAuthConfig normalizedAuthConfig =
                        normalizeAuthConfig(item.getAuthConfig(), existingByKey.get(key));
                normalized.setStatus(normalizeStatus(item.getStatus(), "平台状态仅支持启用或停用"));
                normalized.setAuthType(normalizeAuthType(item.getAuthType(), normalizedAuthConfig));
                normalized.setAuthConfig(normalizedAuthConfig);
                normalizedItems.add(normalized);
            }
        }
        try {
            return JSON.writeValueAsString(normalizedItems);
        } catch (Exception ex) {
            throw new TaskException("平台配置序列化失败", TaskException.Code.UNKNOWN, ex);
        }
    }

    private List<PlatformEndpointItem> parsePlatformItems(String configValue, boolean maskSecrets) {
        if (StringUtils.isBlank(configValue)) {
            return List.of();
        }
        try {
            List<PlatformEndpointItem> rawItems =
                    JSON.readValue(configValue, new TypeReference<List<PlatformEndpointItem>>() {});
            if (rawItems == null) {
                return List.of();
            }
            List<PlatformEndpointItem> items = new ArrayList<>();
            for (PlatformEndpointItem rawItem : rawItems) {
                if (rawItem == null) {
                    continue;
                }
                PlatformEndpointItem item = new PlatformEndpointItem();
                item.setKey(StringUtils.trimToEmpty(rawItem.getKey()));
                item.setName(StringUtils.trimToEmpty(rawItem.getName()));
                item.setApiUrl(StringUtils.trimToEmpty(rawItem.getApiUrl()));
                item.setStatus(rawItem.getStatus() == null ? 1 : rawItem.getStatus());
                item.setAuthType(resolveAuthType(rawItem.getAuthType(), rawItem.getAuthConfig()));
                item.setAuthConfig(copyAuthConfig(rawItem.getAuthConfig(), maskSecrets));
                if (StringUtils.isBlank(item.getKey())
                        && StringUtils.isBlank(item.getName())
                        && StringUtils.isBlank(item.getApiUrl())) {
                    continue;
                }
                items.add(item);
            }
            return items;
        } catch (Exception ex) {
            logger.warn("Failed to parse system config: configKey={}", CONFIG_KEY_SELF_HOSTED_PLATFORMS, ex);
            return List.of();
        }
    }

    private PlatformAuthConfig normalizeAuthConfig(PlatformAuthConfig input, PlatformEndpointItem existing)
            throws TaskException {
        PlatformAuthConfig incoming = input == null ? new PlatformAuthConfig() : input;
        PlatformAuthConfig normalized = new PlatformAuthConfig();
        normalized.setUsername(StringUtils.trimToEmpty(incoming.getUsername()));
        normalized.setPassword(StringUtils.trimToEmpty(incoming.getPassword()));
        normalized.setAppKey(StringUtils.trimToEmpty(incoming.getAppKey()));
        normalized.setAppSecret(StringUtils.trimToEmpty(incoming.getAppSecret()));
        normalized.setRsaPublicKey(StringUtils.trimToEmpty(incoming.getRsaPublicKey()));
        normalized.setTncode(StringUtils.trimToEmpty(incoming.getTncode()));
        normalized.setUserId(StringUtils.trimToEmpty(incoming.getUserId()));

        if (existing != null && existing.getAuthConfig() != null) {
            PlatformAuthConfig existingAuth = existing.getAuthConfig();
            if (StringUtils.isBlank(normalized.getUsername())) {
                normalized.setUsername(StringUtils.trimToEmpty(existingAuth.getUsername()));
            }
            if (StringUtils.isBlank(normalized.getPassword())) {
                normalized.setPassword(StringUtils.trimToEmpty(existingAuth.getPassword()));
            }
            if (StringUtils.isBlank(normalized.getAppKey())) {
                normalized.setAppKey(StringUtils.trimToEmpty(existingAuth.getAppKey()));
            }
            if (StringUtils.isBlank(normalized.getAppSecret())) {
                normalized.setAppSecret(StringUtils.trimToEmpty(existingAuth.getAppSecret()));
            }
            if (StringUtils.isBlank(normalized.getRsaPublicKey())) {
                normalized.setRsaPublicKey(StringUtils.trimToEmpty(existingAuth.getRsaPublicKey()));
            }
            if (StringUtils.isBlank(normalized.getTncode())) {
                normalized.setTncode(StringUtils.trimToEmpty(existingAuth.getTncode()));
            }
            if (StringUtils.isBlank(normalized.getUserId())) {
                normalized.setUserId(StringUtils.trimToEmpty(existingAuth.getUserId()));
            }
        }

        boolean credentialConfigured =
                StringUtils.isNotBlank(normalized.getUsername()) && StringUtils.isNotBlank(normalized.getPassword());
        normalized.setCredentialConfigured(credentialConfigured);
        boolean signatureConfigured =
                StringUtils.isNotBlank(normalized.getAppKey()) && StringUtils.isNotBlank(normalized.getAppSecret());
        normalized.setSignatureConfigured(signatureConfigured);
        return normalized;
    }

    private PlatformAuthConfig copyAuthConfig(PlatformAuthConfig authConfig, boolean maskSecrets) {
        PlatformAuthConfig result = new PlatformAuthConfig();
        PlatformAuthConfig source = authConfig == null ? new PlatformAuthConfig() : authConfig;
        result.setTncode(StringUtils.trimToEmpty(source.getTncode()));
        result.setUserId(StringUtils.trimToEmpty(source.getUserId()));
        boolean configured =
                StringUtils.isNotBlank(source.getUsername()) && StringUtils.isNotBlank(source.getPassword());
        result.setCredentialConfigured(configured);
        boolean signatureConfigured =
                StringUtils.isNotBlank(source.getAppKey()) && StringUtils.isNotBlank(source.getAppSecret());
        result.setSignatureConfigured(signatureConfigured);
        if (maskSecrets) {
            result.setUsername("");
            result.setPassword("");
            result.setAppKey(StringUtils.trimToEmpty(source.getAppKey()));
            result.setAppSecret("");
            result.setRsaPublicKey("");
        } else {
            result.setUsername(StringUtils.trimToEmpty(source.getUsername()));
            result.setPassword(StringUtils.trimToEmpty(source.getPassword()));
            result.setAppKey(StringUtils.trimToEmpty(source.getAppKey()));
            result.setAppSecret(StringUtils.trimToEmpty(source.getAppSecret()));
            result.setRsaPublicKey(StringUtils.trimToEmpty(source.getRsaPublicKey()));
        }
        return result;
    }

    private static boolean isBlankAuthConfig(PlatformAuthConfig authConfig) {
        return authConfig == null
                || (StringUtils.isBlank(authConfig.getUsername())
                        && StringUtils.isBlank(authConfig.getPassword())
                        && StringUtils.isBlank(authConfig.getAppKey())
                        && StringUtils.isBlank(authConfig.getAppSecret())
                        && StringUtils.isBlank(authConfig.getRsaPublicKey())
                        && StringUtils.isBlank(authConfig.getTncode())
                        && StringUtils.isBlank(authConfig.getUserId()));
    }

    private static String normalizePlatformKey(String key) throws TaskException {
        String normalized = StringUtils.trimToEmpty(key);
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        if (!PLATFORM_KEY_PATTERN.matcher(normalized).matches()) {
            throw new TaskException("平台 key 仅支持字母、数字、点、下划线和中划线", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private static String validateApiUrl(String apiUrl) throws TaskException {
        try {
            URI uri = URI.create(apiUrl);
            String scheme = StringUtils.trimToEmpty(uri.getScheme()).toLowerCase();
            if (!uri.isAbsolute() || (!"http".equals(scheme) && !"https".equals(scheme))) {
                throw new TaskException("平台 API URL 仅支持 http 或 https 地址", TaskException.Code.UNKNOWN);
            }
            if (StringUtils.isBlank(uri.getHost())) {
                throw new TaskException("平台 API URL 必须包含合法主机名", TaskException.Code.UNKNOWN);
            }
            String normalized = uri.toString();
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (IllegalArgumentException ex) {
            throw new TaskException("平台 API URL 格式不正确", TaskException.Code.UNKNOWN, ex);
        }
    }

    private long parseInitialGrantTokens(String configValue) {
        if (StringUtils.isBlank(configValue)) {
            return DEFAULT_INITIAL_GRANT_TOKENS;
        }
        try {
            Map<String, Object> payload = JSON.readValue(configValue, new TypeReference<Map<String, Object>>() {});
            Object value = payload == null ? null : payload.get("initialGrantTokens");
            if (value instanceof Number number) {
                return normalizeInitialGrantTokens(number.longValue());
            }
            if (value != null) {
                return normalizeInitialGrantTokens(Long.parseLong(String.valueOf(value)));
            }
        } catch (Exception ex) {
            logger.warn("Failed to parse system config: configKey={}", CONFIG_KEY_TOKEN_QUOTA_SETTINGS, ex);
        }
        return DEFAULT_INITIAL_GRANT_TOKENS;
    }

    private long normalizeInitialGrantTokens(Long value) throws TaskException {
        long normalized = value == null ? DEFAULT_INITIAL_GRANT_TOKENS : value;
        if (normalized < 0L) {
            throw new TaskException("初始 token 额度不能小于 0", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private String normalizeSystemName(String systemName) throws TaskException {
        String normalized = StringUtils.trimToEmpty(systemName);
        if (StringUtils.isBlank(normalized)) {
            return DEFAULT_SYSTEM_NAME;
        }
        if (normalized.length() > MAX_SYSTEM_NAME_LENGTH) {
            throw new TaskException("系统名称长度不能超过 " + MAX_SYSTEM_NAME_LENGTH + " 个字符", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private String normalizeLogoObjectName(String logoObjectName) {
        return StringUtils.trimToEmpty(logoObjectName);
    }

    private String normalizeBrandingLogoExtension(String fileName) throws TaskException {
        String normalized = StringUtils.trimToEmpty(fileName);
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= normalized.length() - 1) {
            throw new TaskException("图标文件缺少扩展名", TaskException.Code.UNKNOWN);
        }
        String extension = normalized.substring(dotIndex).toLowerCase();
        if (!ALLOWED_BRANDING_LOGO_EXTENSIONS.contains(extension)) {
            throw new TaskException("仅支持 png/jpg/jpeg/webp/svg/ico 图标格式", TaskException.Code.UNKNOWN);
        }
        return extension;
    }

    private String extractExtensionFromObjectName(String objectName) {
        String normalized = StringUtils.trimToEmpty(objectName);
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= normalized.length() - 1) {
            return ".png";
        }
        return normalized.substring(dotIndex);
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Integer normalizeStatus(Integer status, String message) throws TaskException {
        if (status == null) {
            return 1;
        }
        if (status != 0 && status != 1) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return status;
    }

    private static String normalizeAuthType(String authType, PlatformAuthConfig authConfig) throws TaskException {
        String normalized = StringUtils.isBlank(authType)
                ? resolveAuthType(null, authConfig)
                : authType.trim().toUpperCase();
        if (!AUTH_TYPE_SYSTEM_TOKEN.equals(normalized) && !AUTH_TYPE_NONE.equals(normalized)) {
            throw new TaskException("authType 仅支持 NONE 或 SYSTEM_TOKEN", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private static String resolveAuthType(String authType, PlatformAuthConfig authConfig) {
        if (StringUtils.isNotBlank(authType)) {
            return authType.trim().toUpperCase();
        }
        boolean hasCredential = authConfig != null
                && StringUtils.isNotBlank(authConfig.getUsername())
                && StringUtils.isNotBlank(authConfig.getPassword());
        return hasCredential ? AUTH_TYPE_SYSTEM_TOKEN : AUTH_TYPE_NONE;
    }

    private static boolean isAdminUser(SysUserModel user) {
        return user != null && user.getUserType() != null && user.getUserType() == UserType.admin.getValue();
    }
}
