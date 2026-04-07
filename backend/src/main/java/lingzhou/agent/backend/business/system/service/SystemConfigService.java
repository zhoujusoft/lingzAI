package lingzhou.agent.backend.business.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lingzhou.agent.backend.business.system.dao.SysUserMapper;
import lingzhou.agent.backend.business.system.dao.SystemConfigMapper;
import lingzhou.agent.backend.business.system.model.PlatformAuthConfig;
import lingzhou.agent.backend.business.system.model.PlatformEndpointItem;
import lingzhou.agent.backend.business.system.model.PlatformSettingsDto;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.system.model.SystemConfigModel;
import lingzhou.agent.backend.business.system.model.UpdatePlatformSettingsInput;
import lingzhou.agent.backend.common.enums.UserType;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemConfigService {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigService.class);
    private static final String CONFIG_KEY_SELF_HOSTED_PLATFORMS = "self_hosted_platforms";
    private static final String AUTH_TYPE_NONE = "NONE";
    private static final String AUTH_TYPE_SYSTEM_TOKEN = "SYSTEM_TOKEN";
    private static final Pattern PLATFORM_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final SystemConfigMapper systemConfigMapper;
    private final SysUserMapper sysUserMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Object schemaMonitor = new Object();
    private volatile boolean schemaReady;

    public SystemConfigService(
            SystemConfigMapper systemConfigMapper, SysUserMapper sysUserMapper, JdbcTemplate jdbcTemplate) {
        this.systemConfigMapper = systemConfigMapper;
        this.sysUserMapper = sysUserMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initializeSchema() {
        ensureSchema();
    }

    public PlatformSettingsDto getPlatformSettings(Long operatorUserId) throws TaskException {
        ensureSchema();
        requireAdmin(operatorUserId);
        return buildPlatformSettingsDto(systemConfigMapper.selectByConfigKey(CONFIG_KEY_SELF_HOSTED_PLATFORMS), true);
    }

    public List<PlatformEndpointItem> getEnabledPlatformItems() {
        ensureSchema();
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

    @Transactional(rollbackFor = Exception.class)
    public PlatformSettingsDto savePlatformSettings(Long operatorUserId, UpdatePlatformSettingsInput input)
            throws TaskException {
        ensureSchema();
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

    private void ensureSchema() {
        if (schemaReady) {
            return;
        }
        synchronized (schemaMonitor) {
            if (schemaReady) {
                return;
            }
            jdbcTemplate.execute(
                    """
                    CREATE TABLE IF NOT EXISTS `system_config` (
                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置主键',
                      `config_key` varchar(120) NOT NULL COMMENT '配置唯一标识',
                      `config_value` longtext COMMENT '配置值，支持 JSON 或普通字符串',
                      `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=停用',
                      `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_system_config_key` (`config_key`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='系统配置表'
                    """);
            schemaReady = true;
        }
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

    private String serializePlatformItems(List<PlatformEndpointItem> platforms, List<PlatformEndpointItem> existingItems)
            throws TaskException {
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
                PlatformAuthConfig normalizedAuthConfig = normalizeAuthConfig(item.getAuthConfig(), existingByKey.get(key));
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
                if (StringUtils.isBlank(item.getKey()) && StringUtils.isBlank(item.getName()) && StringUtils.isBlank(item.getApiUrl())) {
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

    private PlatformAuthConfig normalizeAuthConfig(PlatformAuthConfig input, PlatformEndpointItem existing) throws TaskException {
        PlatformAuthConfig incoming = input == null ? new PlatformAuthConfig() : input;
        PlatformAuthConfig normalized = new PlatformAuthConfig();
        normalized.setUsername(StringUtils.trimToEmpty(incoming.getUsername()));
        normalized.setPassword(StringUtils.trimToEmpty(incoming.getPassword()));
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
        return normalized;
    }

    private PlatformAuthConfig copyAuthConfig(PlatformAuthConfig authConfig, boolean maskSecrets) {
        PlatformAuthConfig result = new PlatformAuthConfig();
        PlatformAuthConfig source = authConfig == null ? new PlatformAuthConfig() : authConfig;
        result.setTncode(StringUtils.trimToEmpty(source.getTncode()));
        result.setUserId(StringUtils.trimToEmpty(source.getUserId()));
        boolean configured = StringUtils.isNotBlank(source.getUsername()) && StringUtils.isNotBlank(source.getPassword());
        result.setCredentialConfigured(configured);
        if (maskSecrets) {
            result.setUsername("");
            result.setPassword("");
        } else {
            result.setUsername(StringUtils.trimToEmpty(source.getUsername()));
            result.setPassword(StringUtils.trimToEmpty(source.getPassword()));
        }
        return result;
    }

    private static boolean isBlankAuthConfig(PlatformAuthConfig authConfig) {
        return authConfig == null
                || (StringUtils.isBlank(authConfig.getUsername())
                        && StringUtils.isBlank(authConfig.getPassword())
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
        String normalized = StringUtils.isBlank(authType) ? resolveAuthType(null, authConfig) : authType.trim().toUpperCase();
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
