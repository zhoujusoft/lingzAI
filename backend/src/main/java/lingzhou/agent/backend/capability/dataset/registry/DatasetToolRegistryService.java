package lingzhou.agent.backend.capability.dataset.registry;

import java.util.Map;
import lingzhou.agent.backend.capability.dataset.runtime.IntegrationDatasetToolRuntimeService;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DatasetToolRegistryService {

    private static final String SEARCH_DATASET_SUMMARY_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "question": {
                  "type": "string",
                  "description": "用户当前的数据问题或查询意图"
                },
                "maxObjects": {
                  "type": "integer",
                  "description": "最多返回多少个候选对象"
                }
              },
              "additionalProperties": false
            }
            """;

    private static final String GET_DATASET_SCHEMA_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "objectCodes": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  },
                  "description": "按对象编码筛选，如 expense_claim_order"
                },
                "objectNames": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  },
                  "description": "按对象名称筛选，如 报销单"
                }
              },
              "additionalProperties": false
            }
            """;

    private static final String EXECUTE_DATASET_SQL_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "sql": {
                  "type": "string",
                  "description": "待执行的查询 SQL，只允许 SELECT/WITH"
                },
                "limit": {
                  "type": "integer",
                  "description": "结果条数上限，最大 200"
                }
              },
              "required": ["sql"],
              "additionalProperties": false
            }
            """;

    private static final String GENERIC_OBJECT_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": true
            }
            """;

    private final ToolCatalogMapper toolCatalogMapper;
    private final IntegrationDatasetToolRuntimeService integrationDatasetToolRuntimeService;

    public DatasetToolRegistryService(
            ToolCatalogMapper toolCatalogMapper,
            IntegrationDatasetToolRuntimeService integrationDatasetToolRuntimeService) {
        this.toolCatalogMapper = toolCatalogMapper;
        this.integrationDatasetToolRuntimeService = integrationDatasetToolRuntimeService;
    }

    public ToolCallback findByName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        ToolCatalog catalog = toolCatalogMapper.selectByToolName(toolName.trim());
        if (catalog == null || !StringUtils.hasText(catalog.getSource()) || !catalog.getSource().startsWith("dataset:")) {
            return null;
        }
        return buildCallback(catalog);
    }

    private ToolCallback buildCallback(ToolCatalog catalog) {
        String toolName = catalog.getToolName();
        if (toolName.endsWith(".search_dataset_summary")) {
            return FunctionToolCallback.builder(
                            toolName,
                            (IntegrationDatasetToolRuntimeService.SearchDatasetSummaryRequest arguments,
                                    org.springframework.ai.chat.model.ToolContext toolContext) -> {
                                try {
                                    return integrationDatasetToolRuntimeService.searchDatasetSummary(toolName, arguments);
                                } catch (lingzhou.agent.backend.common.lzException.TaskException ex) {
                                    throw new IllegalStateException(ex.getMessage(), ex);
                                }
                            })
                    .description(catalog.getDescription())
                    .inputType(IntegrationDatasetToolRuntimeService.SearchDatasetSummaryRequest.class)
                    .inputSchema(SEARCH_DATASET_SUMMARY_SCHEMA)
                    .build();
        }
        if (toolName.endsWith(".get_dataset_schema")) {
            return FunctionToolCallback.builder(
                            toolName,
                            (IntegrationDatasetToolRuntimeService.GetDatasetSchemaRequest arguments,
                                    org.springframework.ai.chat.model.ToolContext toolContext) -> {
                                try {
                                    return integrationDatasetToolRuntimeService.getDatasetSchema(toolName, arguments);
                                } catch (lingzhou.agent.backend.common.lzException.TaskException ex) {
                                    throw new IllegalStateException(ex.getMessage(), ex);
                                }
                            })
                    .description(catalog.getDescription())
                    .inputType(IntegrationDatasetToolRuntimeService.GetDatasetSchemaRequest.class)
                    .inputSchema(GET_DATASET_SCHEMA_SCHEMA)
                    .build();
        }
        if (toolName.endsWith(".execute_dataset_sql")) {
            return FunctionToolCallback.builder(
                            toolName,
                            (IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlRequest arguments,
                                    org.springframework.ai.chat.model.ToolContext toolContext) -> {
                                try {
                                    return integrationDatasetToolRuntimeService.executeDatasetSql(toolName, arguments);
                                } catch (lingzhou.agent.backend.common.lzException.TaskException ex) {
                                    throw new IllegalStateException(ex.getMessage(), ex);
                                }
                            })
                    .description(catalog.getDescription())
                    .inputType(IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlRequest.class)
                    .inputSchema(EXECUTE_DATASET_SQL_SCHEMA)
                    .build();
        }
        return FunctionToolCallback.builder(
                        toolName,
                        (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) ->
                                Map.of("message", "暂不支持的数据集工具类型", "toolName", toolName))
                .description(catalog.getDescription())
                .inputType(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .inputSchema(GENERIC_OBJECT_SCHEMA)
                .build();
    }
}
