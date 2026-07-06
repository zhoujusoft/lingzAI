package lingzhou.agent.backend.capability.api.publish;

import lingzhou.agent.backend.business.integration.domain.IntegrationConnector;
import lingzhou.agent.backend.business.integration.domain.IntegrationConnectorApi;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConnectorApiToolPublishService {

    public static final String TOOL_TYPE_CONNECTOR_API = "CONNECTOR_API";
    private static final String SOURCE_PREFIX = "connector:";

    private final ToolCatalogMapper toolCatalogMapper;

    public ConnectorApiToolPublishService(ToolCatalogMapper toolCatalogMapper) {
        this.toolCatalogMapper = toolCatalogMapper;
    }

    public void publish(IntegrationConnector connector, IntegrationConnectorApi api) {
        if (connector == null || api == null || !StringUtils.hasText(api.getToolName())) {
            return;
        }
        ToolCatalog toolCatalog = toolCatalogMapper.selectByToolName(api.getToolName().trim());
        if (toolCatalog == null) {
            toolCatalog = new ToolCatalog();
            toolCatalog.setToolName(api.getToolName().trim());
            toolCatalog.setSortOrder(61000);
        }
        toolCatalog.setDisplayName(api.getApiName());
        toolCatalog.setDescription(api.getDescription());
        toolCatalog.setToolType(TOOL_TYPE_CONNECTOR_API);
        toolCatalog.setBindable(1);
        if (toolCatalog.getEnabledGlobal() == null) {
            toolCatalog.setEnabledGlobal(0);
        }
        toolCatalog.setOwnerSkillName(null);
        toolCatalog.setSource(buildSource(connector.getId(), api.getApiCode()));
        toolCatalog.setOwnerUserId(connector.getOwnerUserId());
        toolCatalog.setPermissionScope(connector.getPermissionScope());
        if (toolCatalog.getId() == null) {
            toolCatalogMapper.insert(toolCatalog);
        } else {
            toolCatalogMapper.updateById(toolCatalog);
        }
    }

    public void disable(String toolName) {
        toolCatalogMapper.deleteByToolName(toolName);
    }

    public String buildSource(Long connectorId, String apiCode) {
        return SOURCE_PREFIX + connectorId + ":" + (apiCode == null ? "" : apiCode.trim());
    }
}
