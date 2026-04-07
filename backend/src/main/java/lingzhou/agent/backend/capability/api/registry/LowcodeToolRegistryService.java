package lingzhou.agent.backend.capability.api.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.capability.api.client.LowcodePlatformClient;
import lingzhou.agent.backend.business.skill.domain.LowcodeApiCatalog;
import lingzhou.agent.backend.business.skill.service.LowcodeApiCatalogService;
import lingzhou.agent.backend.business.skill.service.LowcodePlatformConfigService;
import lingzhou.agent.backend.business.skill.service.LowcodeTokenService;
import lingzhou.agent.backend.business.system.model.PlatformEndpointItem;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LowcodeToolRegistryService {

    private static final String GENERIC_OBJECT_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": true
            }
            """;

    private final LowcodeApiCatalogService lowcodeApiCatalogService;
    private final LowcodePlatformConfigService lowcodePlatformConfigService;
    private final LowcodeTokenService lowcodeTokenService;
    private final LowcodePlatformClient lowcodePlatformClient;

    public LowcodeToolRegistryService(
            LowcodeApiCatalogService lowcodeApiCatalogService,
            LowcodePlatformConfigService lowcodePlatformConfigService,
            LowcodeTokenService lowcodeTokenService,
            LowcodePlatformClient lowcodePlatformClient) {
        this.lowcodeApiCatalogService = lowcodeApiCatalogService;
        this.lowcodePlatformConfigService = lowcodePlatformConfigService;
        this.lowcodeTokenService = lowcodeTokenService;
        this.lowcodePlatformClient = lowcodePlatformClient;
    }

    public ToolCallback findByName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        LowcodeApiCatalog catalog = lowcodeApiCatalogService.findEnabledByToolName(toolName.trim());
        if (catalog == null) {
            return null;
        }
        return buildCallback(catalog);
    }

    private String buildDescription(LowcodeApiCatalog catalog) {
        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.hasText(catalog.getApiName()) ? catalog.getApiName().trim() : catalog.getToolName());
        if (StringUtils.hasText(catalog.getDescription())) {
            builder.append("。").append(catalog.getDescription().trim());
        }
        builder.append("。调用低代码平台 API，入参会原样透传为 JSON。");
        return builder.toString();
    }

    public ToolCallback buildCallback(LowcodeApiCatalog catalog) {
        return FunctionToolCallback.builder(
                        catalog.getToolName(),
                        (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) ->
                                executeCatalog(catalog, arguments))
                .description(buildDescription(catalog))
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                .inputSchema(GENERIC_OBJECT_SCHEMA)
                .build();
    }

    private Object executeCatalog(LowcodeApiCatalog catalog, Map<String, Object> arguments) {
        try {
            PlatformEndpointItem platform = lowcodePlatformConfigService.requirePlatform(catalog.getPlatformKey());
            String token = lowcodeTokenService.getTokenIfConfigured(platform);
            LowcodePlatformClient.PlatformEnvelope envelope =
                    lowcodePlatformClient.executeApi(platform, token, catalog.getApiCode(), sanitizeArgs(arguments));
            return buildToolResult(envelope);
        } catch (TaskException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private Map<String, Object> sanitizeArgs(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(arguments);
        return payload;
    }

    private Object buildToolResult(LowcodePlatformClient.PlatformEnvelope envelope) {
        if (!StringUtils.hasText(envelope.message())) {
            return envelope.result();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", envelope.message());
        result.put("result", envelope.result());
        return result;
    }
}
