package lingzhou.agent.backend.capability.dataset.runtime;

import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IntegrationDatasetAgentToolRegistry {

    private static final String SUMMARY_TOOL = "search_dataset_summary";
    private static final String SCHEMA_TOOL = "get_dataset_schema";
    private static final String SQL_TOOL = "execute_dataset_sql";

    private final IntegrationDatasetToolRuntimeService runtimeService;

    public IntegrationDatasetAgentToolRegistry(IntegrationDatasetToolRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    public List<ToolCallback> buildCallbacks(String datasetCode) {
        String normalizedDatasetCode = normalizeDatasetCode(datasetCode);
        String datasetToolPrefix = "dataset." + normalizedDatasetCode + ".";
        return List.of(
                FunctionToolCallback.builder(
                                SUMMARY_TOOL,
                                (Map<String, Object> arguments,
                                        org.springframework.ai.chat.model.ToolContext toolContext) ->
                                        runtimeService.searchDatasetSummaryTool(
                                                datasetToolPrefix + SUMMARY_TOOL,
                                                new IntegrationDatasetToolRuntimeService.SearchDatasetSummaryRequest(
                                                        stringValue(arguments.get("question")),
                                                        integerValue(arguments.get("maxObjects")))))
                        .description(
                                "返回当前数据集的业务摘要、候选对象与关系说明，适合在开始分析前先理解数据集。注意：结果中的 objectCode 才是 SQL 里可直接使用的真实表名，objectName 只是中文说明。")
                        .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .inputSchema(
                                """
                                {
                                  \"type\": \"object\",
                                  \"properties\": {
                                    \"question\": { \"type\": \"string\" },
                                    \"maxObjects\": { \"type\": \"integer\" }
                                  }
                                }
                                """)
                        .build(),
                FunctionToolCallback.builder(
                                SCHEMA_TOOL,
                                (Map<String, Object> arguments,
                                        org.springframework.ai.chat.model.ToolContext toolContext) ->
                                        runtimeService.getDatasetSchemaTool(
                                                datasetToolPrefix + SCHEMA_TOOL,
                                                new IntegrationDatasetToolRuntimeService.GetDatasetSchemaRequest(
                                                        stringList(arguments.get("objectCodes")),
                                                        stringList(arguments.get("objectNames")))))
                        .description(
                                "返回当前数据集的对象、字段、子对象和关系结构，可按对象编码或名称过滤。写 SQL 时请使用 objectCode 作为表名，不要使用中文对象名；SQL 字段也必须来自这里返回的结构。")
                        .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .inputSchema(
                                """
                                {
                                  \"type\": \"object\",
                                  \"properties\": {
                                    \"objectCodes\": { \"type\": \"array\", \"items\": { \"type\": \"string\" } },
                                    \"objectNames\": { \"type\": \"array\", \"items\": { \"type\": \"string\" } }
                                  }
                                }
                                """)
                        .build(),
                FunctionToolCallback.builder(
                                SQL_TOOL,
                                (Map<String, Object> arguments,
                                        org.springframework.ai.chat.model.ToolContext toolContext) ->
                                        runtimeService.executeDatasetSqlTool(
                                                datasetToolPrefix + SQL_TOOL,
                                                new IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlRequest(
                                                        stringValue(arguments.get("sql")),
                                                        integerValue(arguments.get("limit")))))
                        .description(
                                "在当前数据集允许范围内执行只读 SQL 查询并返回 rowSchema 和最多 3 条 previewRows。结果少于 3 条时直接返回完整数据且不生成 resultFile；达到 3 条时完整结果会写入 resultFile。resultFile 的 JSON 顶层是对象，固定结构包含 columns、rowSchema、rowCount、rows；Python 读取完整数据必须使用 json.load(f)[\"rows\"]。需要 Python 二次处理或生成文件时，只生成读取 resultFile 的最小脚本并通过 run_python args 传入路径，禁止使用 file_write 重写查询数据或把数据嵌入脚本。仅支持单条只读查询，禁止修改数据。SQL 中表名必须使用 objectCode，不要使用中文对象名；SQL 字段必须来自 get_dataset_schema 返回结果，不能自行想象。若执行失败，结果会返回 success=false、nextActionHint、suggestedSchemaRequest、schemaHints、fieldCandidates，下一步应据此修正 SQL，而不是直接结束。")
                        .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .inputSchema(
                                """
                                {
                                  \"type\": \"object\",
                                  \"required\": [\"sql\"],
                                  \"properties\": {
                                    \"sql\": { \"type\": \"string\" },
                                    \"limit\": { \"type\": \"integer\" }
                                  }
                                }
                                """)
                        .build());
    }

    private String normalizeDatasetCode(String datasetCode) {
        if (!StringUtils.hasText(datasetCode)) {
            throw new IllegalArgumentException("datasetCode 不能为空");
        }
        return datasetCode.trim();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(this::stringValue)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
