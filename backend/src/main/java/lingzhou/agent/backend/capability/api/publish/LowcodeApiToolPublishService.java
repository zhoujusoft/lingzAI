package lingzhou.agent.backend.capability.api.publish;

import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import org.springframework.stereotype.Service;

@Service
public class LowcodeApiToolPublishService {

    private static final String TOOL_TYPE_LOWCODE_API = "LOWCODE_API";
    private static final String SOURCE_PREFIX = "lowcode:";

    private final ToolCatalogMapper toolCatalogMapper;

    public LowcodeApiToolPublishService(ToolCatalogMapper toolCatalogMapper) {
        this.toolCatalogMapper = toolCatalogMapper;
    }

    public void publish(String toolName, String apiName, String description, String platformKey) {
        ToolCatalog toolCatalog = toolCatalogMapper.selectByToolName(toolName);
        if (toolCatalog == null) {
            toolCatalog = new ToolCatalog();
            toolCatalog.setToolName(toolName);
            toolCatalog.setSortOrder(60000);
        }
        toolCatalog.setDisplayName(apiName);
        toolCatalog.setDescription(description);
        toolCatalog.setToolType(TOOL_TYPE_LOWCODE_API);
        toolCatalog.setBindable(1);
        toolCatalog.setOwnerSkillName(null);
        toolCatalog.setSource(buildSource(platformKey));
        if (toolCatalog.getId() == null) {
            toolCatalogMapper.insert(toolCatalog);
        } else {
            toolCatalogMapper.updateById(toolCatalog);
        }
    }

    public void disable(String toolName) {
        toolCatalogMapper.deleteByToolName(toolName);
    }

    private String buildSource(String platformKey) {
        return SOURCE_PREFIX + platformKey;
    }
}
