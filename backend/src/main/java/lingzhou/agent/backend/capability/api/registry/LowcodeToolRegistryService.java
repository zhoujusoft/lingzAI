package lingzhou.agent.backend.capability.api.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    private final LowcodeApiCatalogService lowcodeApiCatalogService;
    private final LowcodePlatformConfigService lowcodePlatformConfigService;
    private final LowcodeTokenService lowcodeTokenService;
    private final LowcodePlatformClient lowcodePlatformClient;
    private final ObjectMapper objectMapper;

    public LowcodeToolRegistryService(
            LowcodeApiCatalogService lowcodeApiCatalogService,
            LowcodePlatformConfigService lowcodePlatformConfigService,
            LowcodeTokenService lowcodeTokenService,
            LowcodePlatformClient lowcodePlatformClient,
            ObjectMapper objectMapper) {
        this.lowcodeApiCatalogService = lowcodeApiCatalogService;
        this.lowcodePlatformConfigService = lowcodePlatformConfigService;
        this.lowcodeTokenService = lowcodeTokenService;
        this.lowcodePlatformClient = lowcodePlatformClient;
        this.objectMapper = objectMapper;
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
        LowcodeApiSchemaResolver.ResolvedSchema resolved =
                LowcodeApiSchemaResolver.resolve(catalog.getRemoteSchemaJson(), objectMapper);
        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.hasText(catalog.getApiName()) ? catalog.getApiName().trim() : catalog.getToolName());
        if (StringUtils.hasText(catalog.getDescription())) {
            builder.append("。").append(catalog.getDescription().trim());
        }
        builder.append("。调用低代码平台 API。");
        if (!resolved.fields().isEmpty()) {
            List<String> fields = new ArrayList<>();
            for (LowcodeApiSchemaResolver.FieldDefinition field : resolved.fields()) {
                StringBuilder fieldText = new StringBuilder(field.key());
                if (StringUtils.hasText(field.label()) && !field.label().equals(field.key())) {
                    fieldText.append("（").append(field.label()).append("）");
                }
                if (field.required()) {
                    fieldText.append("[必填]");
                }
                fields.add(fieldText.toString());
                if (fields.size() >= 10) {
                    break;
                }
            }
            builder.append(" 可用入参：").append(String.join("、", fields)).append("。");
        } else {
            builder.append(" 入参会原样透传为 JSON。");
        }
        return builder.toString();
    }

    public ToolCallback buildCallback(LowcodeApiCatalog catalog) {
        LowcodeApiSchemaResolver.ResolvedSchema resolved =
                LowcodeApiSchemaResolver.resolve(catalog.getRemoteSchemaJson(), objectMapper);
        return FunctionToolCallback.builder(
                        catalog.getToolName(),
                        (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) ->
                                executeCatalog(catalog, arguments))
                .description(buildDescription(catalog))
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                .inputSchema(resolved.jsonSchema())
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
