package lingzhou.agent.backend.capability.dataset.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lingzhou.agent.backend.business.chat.execution.RuntimeExecutionFacade;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionAction;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolContext;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolInvocationContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DatasetResultFileService {

    private static final Logger logger = LoggerFactory.getLogger(DatasetResultFileService.class);
    private static final int MIN_ROWS_TO_PERSIST = 3;
    private static final AtomicLong FILE_SEQUENCE = new AtomicLong();

    private final RuntimeExecutionFacade runtimeExecutionFacade;
    private final ObjectMapper objectMapper;

    public DatasetResultFileService(RuntimeExecutionFacade runtimeExecutionFacade, ObjectMapper objectMapper) {
        this.runtimeExecutionFacade = runtimeExecutionFacade;
        this.objectMapper = objectMapper;
    }

    public String persist(
            IntegrationDatasetToolRuntimeService.ExecuteDatasetSqlResult result, Map<String, Object> rowSchema) {
        RuntimeToolContext context = RuntimeToolInvocationContextHolder.get();
        if (context == null || result == null || result.rowCount() < MIN_ROWS_TO_PERSIST) {
            return "";
        }
        String logicalPath = buildLogicalPath(result.datasetId());
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("datasetId", result.datasetId());
            payload.put("datasetName", result.datasetName());
            payload.put("sql", result.sql());
            payload.put("columns", result.columns());
            payload.put("rowSchema", rowSchema == null ? Map.of() : rowSchema);
            payload.put("rows", result.rows());
            payload.put("rowCount", result.rowCount());
            RuntimeExecutionResult writeResult = runtimeExecutionFacade.execute(
                    context,
                    RuntimeExecutionAction.FILE_WRITE,
                    Map.of("path", logicalPath, "content", objectMapper.writeValueAsString(payload)));
            if (writeResult != null && writeResult.success()) {
                return logicalPath;
            }
            logger.warn(
                    "数据集查询结果落盘失败：datasetId={}, path={}, errorCode={}, error={}",
                    result.datasetId(),
                    logicalPath,
                    writeResult == null ? "" : writeResult.errorCode(),
                    writeResult == null ? "empty result" : writeResult.errorMessage());
        } catch (Exception ex) {
            logger.warn(
                    "数据集查询结果序列化失败：datasetId={}, path={}, error={}",
                    result.datasetId(),
                    logicalPath,
                    ex.getMessage(),
                    ex);
        }
        return "";
    }

    public boolean hasRuntimeContext() {
        return RuntimeToolInvocationContextHolder.get() != null;
    }

    private String buildLogicalPath(Long datasetId) {
        String datasetPart = datasetId == null ? "unknown" : String.valueOf(datasetId);
        return "/workspace/dataset-results/dataset-"
                + datasetPart
                + "-"
                + System.currentTimeMillis()
                + "-"
                + FILE_SEQUENCE.incrementAndGet()
                + ".json";
    }
}
