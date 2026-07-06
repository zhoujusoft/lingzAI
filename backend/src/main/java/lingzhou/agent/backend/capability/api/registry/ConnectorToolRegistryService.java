package lingzhou.agent.backend.capability.api.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.integration.domain.IntegrationConnector;
import lingzhou.agent.backend.business.integration.domain.IntegrationConnectorApi;
import lingzhou.agent.backend.business.integration.mapper.IntegrationConnectorApiMapper;
import lingzhou.agent.backend.business.integration.mapper.IntegrationConnectorMapper;
import lingzhou.agent.backend.capability.api.connector.ConnectorApiExecutor;
import lingzhou.agent.backend.capability.api.connector.ConnectorApiSchemaResolver;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConnectorToolRegistryService {

    private final IntegrationConnectorApiMapper integrationConnectorApiMapper;
    private final IntegrationConnectorMapper integrationConnectorMapper;
    private final ConnectorApiExecutor connectorApiExecutor;
    private final ObjectMapper objectMapper;

    public ConnectorToolRegistryService(
            IntegrationConnectorApiMapper integrationConnectorApiMapper,
            IntegrationConnectorMapper integrationConnectorMapper,
            ConnectorApiExecutor connectorApiExecutor,
            ObjectMapper objectMapper) {
        this.integrationConnectorApiMapper = integrationConnectorApiMapper;
        this.integrationConnectorMapper = integrationConnectorMapper;
        this.connectorApiExecutor = connectorApiExecutor;
        this.objectMapper = objectMapper;
    }

    public ToolCallback findByName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        IntegrationConnectorApi api = integrationConnectorApiMapper.selectByToolName(toolName.trim());
        if (api == null || api.getEnabled() == null || api.getEnabled() != 1) {
            return null;
        }
        IntegrationConnector connector = integrationConnectorMapper.selectById(api.getConnectorId());
        if (connector == null || !"ACTIVE".equalsIgnoreCase(emptyIfNull(connector.getStatus()))) {
            return null;
        }
        return buildCallback(connector, api);
    }

    public ToolCallback buildCallback(IntegrationConnector connector, IntegrationConnectorApi api) {
        ConnectorApiSchemaResolver.ResolvedSchema schema =
                ConnectorApiSchemaResolver.resolve(api.getInputSchemaJson(), objectMapper);
        return FunctionToolCallback.builder(
                        api.getToolName(),
                        (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) ->
                                execute(connector, api, arguments))
                .description(buildDescription(connector, api, schema.fields()))
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                .inputSchema(schema.jsonSchema())
                .build();
    }

    private Object execute(IntegrationConnector connector, IntegrationConnectorApi api, Map<String, Object> arguments) {
        try {
            return connectorApiExecutor.executeForTool(connector, api, arguments);
        } catch (TaskException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private String buildDescription(
            IntegrationConnector connector,
            IntegrationConnectorApi api,
            List<ConnectorApiSchemaResolver.FieldDefinition> fields) {
        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.hasText(api.getApiName()) ? api.getApiName().trim() : api.getToolName());
        if (StringUtils.hasText(api.getDescription())) {
            builder.append("。").append(api.getDescription().trim());
        }
        builder.append("。调用自定义外部 API");
        if (connector != null && StringUtils.hasText(connector.getName())) {
            builder.append("，连接器：").append(connector.getName().trim());
        }
        if (!fields.isEmpty()) {
            List<String> summaries = new ArrayList<>();
            for (ConnectorApiSchemaResolver.FieldDefinition field : fields) {
                StringBuilder item = new StringBuilder(field.key());
                if (StringUtils.hasText(field.label()) && !field.label().equals(field.key())) {
                    item.append("（").append(field.label()).append("）");
                }
                if (field.required()) {
                    item.append("[必填]");
                }
                summaries.add(item.toString());
                if (summaries.size() >= 10) {
                    break;
                }
            }
            builder.append("。可用入参：").append(String.join("、", summaries)).append("。");
        }
        return builder.toString();
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value.trim();
    }
}
