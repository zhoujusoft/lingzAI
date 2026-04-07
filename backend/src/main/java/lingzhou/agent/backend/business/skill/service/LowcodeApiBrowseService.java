package lingzhou.agent.backend.business.skill.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.api.client.LowcodePlatformClient;
import lingzhou.agent.backend.business.skill.domain.LowcodeApiCatalog;
import lingzhou.agent.backend.business.system.model.PlatformEndpointItem;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LowcodeApiBrowseService {

    private final LowcodePlatformConfigService lowcodePlatformConfigService;
    private final LowcodeTokenService lowcodeTokenService;
    private final LowcodePlatformClient lowcodePlatformClient;
    private final LowcodeApiCatalogService lowcodeApiCatalogService;

    public LowcodeApiBrowseService(
            LowcodePlatformConfigService lowcodePlatformConfigService,
            LowcodeTokenService lowcodeTokenService,
            LowcodePlatformClient lowcodePlatformClient,
            LowcodeApiCatalogService lowcodeApiCatalogService) {
        this.lowcodePlatformConfigService = lowcodePlatformConfigService;
        this.lowcodeTokenService = lowcodeTokenService;
        this.lowcodePlatformClient = lowcodePlatformClient;
        this.lowcodeApiCatalogService = lowcodeApiCatalogService;
    }

    public List<PlatformOption> listPlatforms() {
        return lowcodePlatformConfigService.listEnabledPlatforms().stream()
                .map(platform -> new PlatformOption(platform.getKey(), platform.getName(), platform.getApiUrl()))
                .toList();
    }

    public List<AppView> listApps(String platformKey) throws TaskException {
        PlatformEndpointItem platform = lowcodePlatformConfigService.requirePlatform(platformKey);
        String token = lowcodeTokenService.getTokenIfConfigured(platform);
        return lowcodePlatformClient.getAppList(platform, token).stream()
                .sorted(
                        Comparator.comparing(
                                        this::extractAppCreatedAtMillis,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(item -> firstNonBlank(item.get("name"), item.get("code"))))
                .map(this::toAppView)
                .toList();
    }

    public List<ApiView> listApis(String platformKey, String appId) throws TaskException {
        if (!StringUtils.hasText(appId)) {
            throw new TaskException("appId 不能为空", TaskException.Code.UNKNOWN);
        }
        PlatformEndpointItem platform = lowcodePlatformConfigService.requirePlatform(platformKey);
        String token = lowcodeTokenService.getTokenIfConfigured(platform);
        Map<String, LowcodeApiCatalog> registeredMap = lowcodeApiCatalogService.mapByPlatformAndApiCode(platformKey);
        return lowcodePlatformClient.loadApiList(platform, token, appId).stream()
                .map(item -> toApiView(item, registeredMap.get(asText(item.get("apiCode")))))
                .toList();
    }

    public TestExecuteResult testExecute(String platformKey, String apiCode, Map<String, Object> arguments)
            throws TaskException {
        if (!StringUtils.hasText(apiCode)) {
            throw new TaskException("apiCode 不能为空", TaskException.Code.UNKNOWN);
        }
        PlatformEndpointItem platform = lowcodePlatformConfigService.requirePlatform(platformKey);
        String token = lowcodeTokenService.getTokenIfConfigured(platform);
        LowcodePlatformClient.PlatformEnvelope envelope =
                lowcodePlatformClient.executeApi(platform, token, apiCode.trim(), arguments == null ? Map.of() : arguments);
        return new TestExecuteResult(
                platformKey.trim(),
                apiCode.trim(),
                envelope.status(),
                envelope.code(),
                envelope.message(),
                envelope.result());
    }

    private AppView toAppView(Map<String, Object> item) {
        return new AppView(
                asText(item.get("objectId")),
                firstNonBlank(item.get("name"), item.get("code")),
                asText(item.get("code")),
                asText(item.get("logo")),
                asText(item.get("remark")),
                asInteger(item.get("apiCount")));
    }

    private ApiView toApiView(Map<String, Object> item, LowcodeApiCatalog registered) {
        return new ApiView(
                asText(item.get("objectId")),
                asText(item.get("parentId")),
                firstNonBlank(item.get("apiName"), item.get("name")),
                asText(item.get("apiCode")),
                asText(item.get("apiRemark")),
                asText(item.get("method")),
                asText(item.get("url")),
                item.get("inputParams"),
                item.get("outputParams"),
                item.get("headers"),
                item.get("body"),
                item.get("sqlText"),
                registered != null && registered.getEnabled() != null && registered.getEnabled() == 1,
                registered == null ? "" : asText(registered.getToolName()),
                item);
    }

    private String firstNonBlank(Object first, Object second) {
        String firstText = asText(first);
        return StringUtils.hasText(firstText) ? firstText : asText(second);
    }

    private String asText(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return text;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long extractAppCreatedAtMillis(Map<String, Object> item) {
        return firstNonNullTimestamp(
                item.get("createTime"),
                item.get("createdAt"),
                item.get("createdTime"),
                item.get("create_time"),
                item.get("created_at"),
                item.get("creationTime"),
                item.get("gmtCreate"));
    }

    private Long firstNonNullTimestamp(Object... values) {
        for (Object value : values) {
            Long timestamp = toTimestampMillis(value);
            if (timestamp != null) {
                return timestamp;
            }
        }
        return null;
    }

    private Long toTimestampMillis(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long raw = number.longValue();
            return raw > 100000000000L ? raw : raw * 1000L;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (DateTimeParseException ignore) {
            // Try additional common datetime formats below.
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (DateTimeParseException ignore) {
            // Fall through to numeric parsing.
        }
        try {
            long raw = Long.parseLong(text);
            return raw > 100000000000L ? raw : raw * 1000L;
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    public record PlatformOption(String key, String name, String apiUrl) {}

    public record AppView(String appId, String name, String code, String logo, String remark, Integer apiCount) {}

    public record ApiView(
            String apiId,
            String appId,
            String apiName,
            String apiCode,
            String apiRemark,
            String method,
            String url,
            Object inputParams,
            Object outputParams,
            Object headers,
            Object body,
            Object sqlText,
            boolean registered,
            String toolName,
            Map<String, Object> raw) {}

    public record TestExecuteResult(
            String platformKey, String apiCode, int status, int code, String message, Object result) {}
}
