package lingzhou.agent.backend.capability.dataset.registry;

import java.util.Map;
import lingzhou.agent.backend.capability.dataset.runtime.KnowledgeBaseToolRuntimeService;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeBaseToolRegistryService {

    private static final String GENERIC_OBJECT_SCHEMA = """
            {
              \"type\": \"object\",
              \"additionalProperties\": true
            }
            """;

    private final ToolCatalogMapper toolCatalogMapper;
    private final KnowledgeBaseToolRuntimeService knowledgeBaseToolRuntimeService;

    public KnowledgeBaseToolRegistryService(
            ToolCatalogMapper toolCatalogMapper, KnowledgeBaseToolRuntimeService knowledgeBaseToolRuntimeService) {
        this.toolCatalogMapper = toolCatalogMapper;
        this.knowledgeBaseToolRuntimeService = knowledgeBaseToolRuntimeService;
    }

    public ToolCallback findByName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        ToolCatalog catalog = toolCatalogMapper.selectByToolName(toolName.trim());
        if (catalog == null || !StringUtils.hasText(catalog.getSource()) || !catalog.getSource().startsWith("knowledge_base:")) {
            return null;
        }
        return buildCallback(catalog);
    }

    private ToolCallback buildCallback(ToolCatalog catalog) {
        String toolName = catalog.getToolName();
        if (toolName.endsWith(".search")) {
            return FunctionToolCallback.builder(
                            toolName,
                            (KnowledgeBaseToolRuntimeService.SearchKnowledgeBaseRequest arguments,
                                    org.springframework.ai.chat.model.ToolContext toolContext) -> {
                                try {
                                    return knowledgeBaseToolRuntimeService.search(toolName, arguments);
                                } catch (lingzhou.agent.backend.common.lzException.TaskException ex) {
                                    throw new IllegalStateException(ex.getMessage(), ex);
                                }
                            })
                    .description(catalog.getDescription())
                    .inputType(KnowledgeBaseToolRuntimeService.SearchKnowledgeBaseRequest.class)
                    .inputSchema(GENERIC_OBJECT_SCHEMA)
                    .build();
        }
        return FunctionToolCallback.builder(
                        toolName,
                        (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) ->
                                Map.of("message", "暂不支持的知识库工具类型", "toolName", toolName))
                .description(catalog.getDescription())
                .inputType(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .inputSchema(GENERIC_OBJECT_SCHEMA)
                .build();
    }
}
