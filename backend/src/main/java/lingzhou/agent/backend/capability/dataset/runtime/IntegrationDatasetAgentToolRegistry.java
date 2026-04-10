package lingzhou.agent.backend.capability.dataset.runtime;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.common.lzException.TaskException;
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
                                (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) ->
                                        invoke(() -> runtimeService.searchDatasetSummary(
                                                datasetToolPrefix + SUMMARY_TOOL,
                                                new IntegrationDatasetToolRuntimeService.SearchDatasetSummaryRequest(
                                                        stringValue(arguments.get("question")),
                                                        integerValue(arguments.get("maxObjects"))))))
                        .description("返回当前数据集的业务摘要、候选对象与关系说明，适合在开始分析前先理解数据集。注意：结果中的 objectCode 才是 SQL 里可直接使用的真实表名，objectName 只是中文说明。")
                        .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .inputSchema("""
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
                                (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) ->
                                        invoke(() -> runtimeService.getDatasetSchema(
                                                datasetToolPrefix + SCHEMA_TOOL,
                                                new IntegrationDatasetToolRuntimeService.GetDatasetSchemaRequest(
                                                        stringList(arguments.get("objectCodes")),
                                                        stringList(arguments.get("objectNames"))))))
                        .description("返回当前数据集的对象、字段、子对象和关系结构，可按对象编码或名称过滤。写 SQL 时请使用 objectCode 作为表名，不要使用中文对象名；SQL 字段也必须来自这里返回的结构。")
                        .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .inputSchema("""
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
                                (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) ->
                                        invoke(() -> runtimeService.executeDatasetSql(
                                                datasetToolPrefix + SQL_TOOL,
                                                new IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlRequest(
                                                        stringValue(arguments.get("sql")),
                                                        integerValue(arguments.get("limit"))))))
                        .description("在当前数据集允许范围内执行只读 SQL 查询并返回结果。仅支持单条只读查询，禁止修改数据。SQL 中表名必须使用 objectCode，不要使用中文对象名；SQL 字段必须来自 get_dataset_schema 返回结果，不能自行想象。")
                        .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .inputSchema("""
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

    private <T> T invoke(TaskSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (TaskException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
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
        return list.stream().map(this::stringValue).filter(item -> !item.isBlank()).toList();
    }

    @FunctionalInterface
    private interface TaskSupplier<T> {
        T get() throws TaskException;
    }
}
