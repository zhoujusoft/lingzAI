package lingzhou.agent.backend.capability.dataset.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.RuntimeExecutionFacade;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionAction;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionMode;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolContext;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolInvocationContextHolder;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetService;
import lingzhou.agent.backend.business.integration.mapper.IntegrationDataSourceMapper;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DatasetResultFileServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearContext() {
        RuntimeToolInvocationContextHolder.clear();
    }

    @Test
    void persistsCompleteDatasetResultIntoRuntimeWorkspace() throws Exception {
        TrackingRuntimeExecutionFacade facade = new TrackingRuntimeExecutionFacade();
        DatasetResultFileService service = new DatasetResultFileService(facade, objectMapper);
        RuntimeToolInvocationContextHolder.set(buildContext());
        IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult result =
                new IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult(
                        12L,
                        "经营指标",
                        "SELECT * FROM metric",
                        List.of("name", "actual"),
                        List.of(
                                Map.of("name", "新签合同额", "actual", 100),
                                Map.of("name", "营业收入", "actual", 80),
                                Map.of("name", "利润总额", "actual", 20)),
                        3);

        Map<String, Object> rowSchema = Map.of("type", "object");
        String resultFile = service.persist(result, rowSchema);

        assertThat(resultFile).startsWith("/workspace/dataset-results/dataset-12-").endsWith(".json");
        assertThat(facade.lastAction).isEqualTo(RuntimeExecutionAction.FILE_WRITE);
        assertThat(facade.lastPayload.get("path")).isEqualTo(resultFile);
        JsonNode written = objectMapper.readTree(String.valueOf(facade.lastPayload.get("content")));
        assertThat(written.path("rowCount").asInt()).isEqualTo(3);
        assertThat(written.path("rowSchema").path("type").asText()).isEqualTo("object");
        assertThat(written.path("rows").get(0).path("name").asText()).isEqualTo("新签合同额");
    }

    @Test
    void doesNotPersistDatasetResultWithFewerThanThreeRows() {
        TrackingRuntimeExecutionFacade facade = new TrackingRuntimeExecutionFacade();
        DatasetResultFileService service = new DatasetResultFileService(facade, objectMapper);
        RuntimeToolInvocationContextHolder.set(buildContext());
        IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult result =
                new IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult(
                        12L,
                        "经营指标",
                        "SELECT * FROM metric",
                        List.of("name"),
                        List.of(Map.of("name", "新签合同额"), Map.of("name", "营业收入")),
                        2);

        String resultFile = service.persist(result, Map.of("type", "object"));

        assertThat(resultFile).isEmpty();
        assertThat(facade.lastAction).isNull();
    }

    @Test
    void toolResultReturnsThreeRowPreviewSchemaAndResultFile() {
        DatasetResultFileService resultFileService = mock(DatasetResultFileService.class);
        IntegrationDatasetToolRuntimeService service = new IntegrationDatasetToolRuntimeService(
                mock(ToolCatalogMapper.class),
                mock(IntegrationDatasetService.class),
                mock(IntegrationDataSourceMapper.class),
                mock(LowcodeDatasetSqlExecutor.class),
                resultFileService);
        List<Map<String, Object>> rows =
                java.util.stream.IntStream.range(0, 25).mapToObj(index -> Map.<String, Object>of("index", index)).toList();
        IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult result =
                new IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult(
                        12L, "经营指标", "SELECT * FROM metric", List.of("index", "rate"), rows, rows.size());
        when(resultFileService.persist(org.mockito.ArgumentMatchers.eq(result), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn("/workspace/dataset-results/result.json");
        when(resultFileService.hasRuntimeContext()).thenReturn(true);

        IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlToolResult toolResult = service.toToolResult(result);

        assertThat(toolResult.previewRows()).hasSize(3);
        assertThat(toolResult.rowCount()).isEqualTo(25);
        assertThat(toolResult.rowsTruncated()).isTrue();
        assertThat(toolResult.resultFile()).isEqualTo("/workspace/dataset-results/result.json");
        assertThat(toolResult.rowSchema()).containsEntry("type", "object");
        JsonNode rowSchema = objectMapper.valueToTree(toolResult.rowSchema());
        assertThat(rowSchema.path("properties").path("index").path("type").asText())
                .isEqualTo("integer");
        assertThat(rowSchema.path("properties").path("rate").path("type").asText())
                .isEqualTo("null");
        assertThat(toolResult.nextActionHint())
                .contains("json.load(f)[\"rows\"]")
                .contains("最小脚本")
                .contains("禁止使用 file_write");
    }

    @Test
    void toolResultReturnsAllRowsWithoutPersistingWhenFewerThanThreeRows() {
        DatasetResultFileService resultFileService = mock(DatasetResultFileService.class);
        IntegrationDatasetToolRuntimeService service = new IntegrationDatasetToolRuntimeService(
                mock(ToolCatalogMapper.class),
                mock(IntegrationDatasetService.class),
                mock(IntegrationDataSourceMapper.class),
                mock(LowcodeDatasetSqlExecutor.class),
                resultFileService);
        List<Map<String, Object>> rows =
                List.of(Map.of("name", "新签合同额", "actual", 100), Map.of("name", "营业收入", "actual", 80));
        IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult result =
                new IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult(
                        12L, "经营指标", "SELECT * FROM metric", List.of("name", "actual"), rows, rows.size());
        when(resultFileService.hasRuntimeContext()).thenReturn(true);

        IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlToolResult toolResult = service.toToolResult(result);

        assertThat(toolResult.previewRows()).containsExactlyElementsOf(rows);
        assertThat(toolResult.rowsTruncated()).isFalse();
        assertThat(toolResult.resultFile()).isEmpty();
        assertThat(toolResult.nextActionHint()).contains("少于 3 条").contains("无需读取 JSON 文件");
        verify(resultFileService, never())
                .persist(org.mockito.ArgumentMatchers.eq(result), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void toolResultDoesNotReturnAllRowsWhenRuntimePersistenceFails() {
        DatasetResultFileService resultFileService = mock(DatasetResultFileService.class);
        IntegrationDatasetToolRuntimeService service = new IntegrationDatasetToolRuntimeService(
                mock(ToolCatalogMapper.class),
                mock(IntegrationDatasetService.class),
                mock(IntegrationDataSourceMapper.class),
                mock(LowcodeDatasetSqlExecutor.class),
                resultFileService);
        List<Map<String, Object>> rows =
                java.util.stream.IntStream.range(0, 25).mapToObj(index -> Map.<String, Object>of("index", index)).toList();
        IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult result =
                new IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult(
                        12L, "经营指标", "SELECT * FROM metric", List.of("index"), rows, rows.size());
        when(resultFileService.persist(org.mockito.ArgumentMatchers.eq(result), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn("");
        when(resultFileService.hasRuntimeContext()).thenReturn(true);

        IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlToolResult toolResult = service.toToolResult(result);

        assertThat(toolResult.previewRows()).hasSize(3);
        assertThat(toolResult.rowsTruncated()).isTrue();
        assertThat(toolResult.resultFile()).isEmpty();
        assertThat(toolResult.nextActionHint()).contains("写入 runtime 文件失败").contains("缩小查询范围");
    }

    private RuntimeToolContext buildContext() {
        return new RuntimeToolContext(
                "session-1",
                1L,
                1L,
                LingzRuntimeScopeType.GENERAL,
                null,
                "",
                () -> "",
                RuntimeExecutionMode.NATIVE,
                "[]",
                "{}",
                false,
                "",
                1L,
                2L);
    }

    private static final class TrackingRuntimeExecutionFacade extends RuntimeExecutionFacade {

        private RuntimeExecutionAction lastAction;
        private Map<String, Object> lastPayload;

        private TrackingRuntimeExecutionFacade() {
            super(null, null, null, null, null, null);
        }

        @Override
        public RuntimeExecutionResult execute(
                RuntimeToolContext toolContext, RuntimeExecutionAction action, Map<String, Object> payload) {
            this.lastAction = action;
            this.lastPayload = payload;
            return RuntimeExecutionResult.success(action, "ok", Map.of("path", payload.get("path")));
        }
    }
}
