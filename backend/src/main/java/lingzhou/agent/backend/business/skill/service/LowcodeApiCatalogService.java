package lingzhou.agent.backend.business.skill.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lingzhou.agent.backend.capability.api.publish.LowcodeApiToolPublishService;
import lingzhou.agent.backend.business.skill.domain.LowcodeApiCatalog;
import lingzhou.agent.backend.business.skill.mapper.LowcodeApiCatalogMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillToolBindingMapper;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LowcodeApiCatalogService {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final LowcodeApiCatalogMapper lowcodeApiCatalogMapper;
    private final ToolCatalogMapper toolCatalogMapper;
    private final LowcodeApiToolPublishService lowcodeApiToolPublishService;
    private final SkillToolBindingMapper skillToolBindingMapper;
    private final JdbcTemplate jdbcTemplate;

    public LowcodeApiCatalogService(
            LowcodeApiCatalogMapper lowcodeApiCatalogMapper,
            ToolCatalogMapper toolCatalogMapper,
            LowcodeApiToolPublishService lowcodeApiToolPublishService,
            SkillToolBindingMapper skillToolBindingMapper,
            JdbcTemplate jdbcTemplate) {
        this.lowcodeApiCatalogMapper = lowcodeApiCatalogMapper;
        this.toolCatalogMapper = toolCatalogMapper;
        this.lowcodeApiToolPublishService = lowcodeApiToolPublishService;
        this.skillToolBindingMapper = skillToolBindingMapper;
        this.jdbcTemplate = jdbcTemplate;
    }


    public Map<String, LowcodeApiCatalog> mapByPlatformAndApiCode(String platformKey) {
        if (!StringUtils.hasText(platformKey)) {
            return Map.of();
        }
        Map<String, LowcodeApiCatalog> result = new LinkedHashMap<>();
        for (LowcodeApiCatalog item : lowcodeApiCatalogMapper.selectByPlatformKey(platformKey.trim())) {
            if (item != null && StringUtils.hasText(item.getApiCode())) {
                result.put(item.getApiCode().trim(), item);
            }
        }
        return result;
    }

    public LowcodeApiCatalog findEnabledByToolName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        LowcodeApiCatalog catalog = lowcodeApiCatalogMapper.selectByToolName(toolName.trim());
        if (catalog == null || catalog.getEnabled() == null || catalog.getEnabled() != 1) {
            return null;
        }
        return catalog;
    }

    @Transactional(rollbackFor = Exception.class)
    public RegistrationView register(RegisterCommand command) throws TaskException {
        String platformKey = normalizeRequired(command.platformKey(), "platformKey 不能为空");
        String apiCode = normalizeRequired(command.apiCode(), "apiCode 不能为空");
        String apiName = normalizeRequired(command.apiName(), "apiName 不能为空");
        String appId = normalizeText(command.appId());
        String appName = normalizeText(command.appName());
        String apiId = normalizeText(command.apiId());
        String description = normalizeText(command.description());
        String toolName = normalizeToolName(command.toolName(), platformKey, apiCode);
        String remoteSchemaJson = serializeJson(command.remoteSchema());

        LowcodeApiCatalog catalog = lowcodeApiCatalogMapper.selectByPlatformKeyAndApiCode(platformKey, apiCode);
        boolean created = catalog == null;
        if (catalog == null) {
            catalog = new LowcodeApiCatalog();
            catalog.setPlatformKey(platformKey);
            catalog.setApiCode(apiCode);
        }
        catalog.setAppId(appId);
        catalog.setAppName(appName);
        catalog.setApiId(apiId);
        catalog.setApiName(apiName);
        catalog.setDescription(description);
        catalog.setRemoteSchemaJson(remoteSchemaJson);
        catalog.setToolName(toolName);
        catalog.setEnabled(1);
        catalog.setLastSyncAt(new Date());
        if (created) {
            lowcodeApiCatalogMapper.insert(catalog);
        } else {
            lowcodeApiCatalogMapper.updateById(catalog);
        }

        lowcodeApiToolPublishService.publish(toolName, apiName, description, platformKey);

        return new RegistrationView(
                catalog.getId(),
                platformKey,
                apiCode,
                apiName,
                toolName,
                created,
                false);
    }

    @Transactional(rollbackFor = Exception.class)
    public UnregisterResult unregister(String platformKey, String apiCode) throws TaskException {
        String normalizedPlatformKey = normalizeRequired(platformKey, "platformKey 不能为空");
        String normalizedApiCode = normalizeRequired(apiCode, "apiCode 不能为空");
        LowcodeApiCatalog catalog =
                lowcodeApiCatalogMapper.selectByPlatformKeyAndApiCode(normalizedPlatformKey, normalizedApiCode);
        if (catalog == null) {
            throw new TaskException("低代码 API 未注册", TaskException.Code.UNKNOWN);
        }
        String toolName = normalizeText(catalog.getToolName());
        if (StringUtils.hasText(toolName)) {
            skillToolBindingMapper.deleteByToolNames(List.of(toolName));
            lowcodeApiToolPublishService.disable(toolName);
        }
        lowcodeApiCatalogMapper.deleteByPlatformKeyAndApiCode(normalizedPlatformKey, normalizedApiCode);
        return new UnregisterResult(normalizedPlatformKey, normalizedApiCode, toolName);
    }

    private String normalizeToolName(String rawToolName, String platformKey, String apiCode) throws TaskException {
        String candidate = StringUtils.hasText(rawToolName)
                ? rawToolName.trim()
                : ("lowcode." + platformKey + "." + apiCode).trim();
        candidate = candidate.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (!StringUtils.hasText(candidate)) {
            throw new TaskException("生成工具名失败", TaskException.Code.UNKNOWN);
        }
        LowcodeApiCatalog existingByToolName = lowcodeApiCatalogMapper.selectByToolName(candidate);
        if (existingByToolName != null
                && (!Objects.equals(existingByToolName.getPlatformKey(), platformKey)
                        || !Objects.equals(existingByToolName.getApiCode(), apiCode))) {
            throw new TaskException("工具名已被其他低代码 API 使用：" + candidate, TaskException.Code.UNKNOWN);
        }
        ToolCatalog existingTool = toolCatalogMapper.selectByToolName(candidate);
        if (existingTool != null && !Objects.equals(existingTool.getToolType(), "LOWCODE_API")) {
            throw new TaskException("工具名已被占用：" + candidate, TaskException.Code.UNKNOWN);
        }
        return candidate;
    }

    private String serializeJson(Object value) throws TaskException {
        if (value == null) {
            return "";
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ex) {
            throw new TaskException("低代码 API 元数据序列化失败", TaskException.Code.UNKNOWN, ex);
        }
    }

    private String normalizeRequired(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    public record RegisterCommand(
            String platformKey,
            String appId,
            String appName,
            String apiId,
            String apiCode,
            String apiName,
            String description,
            String toolName,
            Object remoteSchema) {}

    public record RegistrationView(
            Long id,
            String platformKey,
            String apiCode,
            String apiName,
            String toolName,
            boolean created,
            boolean bindable) {}

    public record UnregisterResult(String platformKey, String apiCode, String toolName) {}
}
