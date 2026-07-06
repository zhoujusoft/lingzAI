package lingzhou.agent.backend.capability.dataset.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetService;
import lingzhou.agent.backend.business.integration.domain.IntegrationDataSource;
import lingzhou.agent.backend.business.integration.mapper.IntegrationDataSourceMapper;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IntegrationDatasetToolRuntimeService {

    private static final Pattern FROM_JOIN_PATTERN = Pattern.compile("(?i)\\b(?:from|join)\\s+([`\"\\w.]+)");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\blimit\\s+\\d+");
    private static final Pattern UNKNOWN_COLUMN_PATTERN = Pattern.compile("(?i)unknown column '([^']+)'");
    private static final List<String> FORBIDDEN_SQL_KEYWORDS = List.of(
            "insert", "update", "delete", "drop", "alter", "truncate", "create", "grant", "revoke", "merge", "replace");
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;
    private static final int TOOL_PREVIEW_ROW_LIMIT = 3;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ToolCatalogMapper toolCatalogMapper;
    private final IntegrationDatasetService integrationDatasetService;
    private final IntegrationDataSourceMapper integrationDataSourceMapper;
    private final LowcodeDatasetSqlExecutor lowcodeDatasetSqlExecutor;
    private final DatasetResultFileService datasetResultFileService;

    public IntegrationDatasetToolRuntimeService(
            ToolCatalogMapper toolCatalogMapper,
            IntegrationDatasetService integrationDatasetService,
            IntegrationDataSourceMapper integrationDataSourceMapper,
            LowcodeDatasetSqlExecutor lowcodeDatasetSqlExecutor,
            DatasetResultFileService datasetResultFileService) {
        this.toolCatalogMapper = toolCatalogMapper;
        this.integrationDatasetService = integrationDatasetService;
        this.integrationDataSourceMapper = integrationDataSourceMapper;
        this.lowcodeDatasetSqlExecutor = lowcodeDatasetSqlExecutor;
        this.datasetResultFileService = datasetResultFileService;
    }

    public SearchDatasetSummaryResult searchDatasetSummary(String toolName, SearchDatasetSummaryRequest request)
            throws TaskException {
        ResolvedDataset resolved = resolveDataset(toolName);
        return searchDatasetSummary(resolved, request);
    }

    public Object searchDatasetSummaryTool(String toolName, SearchDatasetSummaryRequest request) {
        try {
            ResolvedDataset resolved = resolveDataset(toolName);
            return searchDatasetSummary(resolved, request);
        } catch (TaskException ex) {
            return buildToolFailureResult(
                    "SEARCH_DATASET_SUMMARY", null, ex, List.of(), List.of(), List.of(), "", null, "", List.of());
        }
    }

    private SearchDatasetSummaryResult searchDatasetSummary(
            ResolvedDataset resolved, SearchDatasetSummaryRequest request) {
        IntegrationDatasetService.DatasetDetail detail = resolved.detail();
        String question = trimText(request == null ? "" : request.question());
        int maxObjects = normalizeMaxObjects(request == null ? null : request.maxObjects());
        List<ObjectScore> rankedObjects = rankObjects(detail, question);
        List<ObjectSummary> candidateObjects = rankedObjects.stream()
                .limit(maxObjects)
                .map(item -> item.summary())
                .toList();
        String summary = buildDatasetSummary(detail);
        String relationDescription = buildRelationDescription(detail, candidateObjects);
        return new SearchDatasetSummaryResult(
                detail.id(), detail.name(), detail.sourceKind(), summary, relationDescription, candidateObjects);
    }

    public DatasetSchemaResult getDatasetSchema(String toolName, GetDatasetSchemaRequest request) throws TaskException {
        ResolvedDataset resolved = resolveDataset(toolName);
        return getDatasetSchema(resolved, request);
    }

    public Object getDatasetSchemaTool(String toolName, GetDatasetSchemaRequest request) {
        try {
            ResolvedDataset resolved = resolveDataset(toolName);
            return getDatasetSchema(resolved, request);
        } catch (TaskException ex) {
            return buildToolFailureResult(
                    "GET_DATASET_SCHEMA", null, ex, List.of(), List.of(), List.of(), "", null, "", List.of());
        }
    }

    private DatasetSchemaResult getDatasetSchema(ResolvedDataset resolved, GetDatasetSchemaRequest request) {
        IntegrationDatasetService.DatasetDetail detail = resolved.detail();
        Set<String> requestedCodes = normalizeCodeSet(request == null ? List.of() : request.objectCodes());
        Set<String> requestedNames = normalizeCodeSet(request == null ? List.of() : request.objectNames());
        List<ObjectSchema> objects = new ArrayList<>();
        for (IntegrationDatasetService.ObjectBindingView objectBinding : detail.objectBindings()) {
            if (!isSelected(objectBinding.selected())) {
                continue;
            }
            if (!matchesObjectFilter(objectBinding, requestedCodes, requestedNames)) {
                continue;
            }
            objects.add(toObjectSchema(detail, objectBinding));
        }
        if (objects.isEmpty() && requestedCodes.isEmpty() && requestedNames.isEmpty()) {
            for (IntegrationDatasetService.ObjectBindingView objectBinding : detail.objectBindings()) {
                if (isSelected(objectBinding.selected())) {
                    objects.add(toObjectSchema(detail, objectBinding));
                }
            }
        }
        return new DatasetSchemaResult(detail.id(), detail.name(), detail.sourceKind(), objects);
    }

    public ExecuteDatasetSqlResult executeDatasetSql(String toolName, ExecuteDatasetSqlRequest request)
            throws TaskException {
        ResolvedDataset resolved = resolveDataset(toolName);
        return executeDatasetSql(resolved, request);
    }

    public Object executeDatasetSqlTool(String toolName, ExecuteDatasetSqlRequest request) {
        ResolvedDataset resolved = null;
        try {
            resolved = resolveDataset(toolName);
            return toToolResult(executeDatasetSql(resolved, request));
        } catch (TaskException ex) {
            return buildSqlFailureResult(resolved, request, ex);
        }
    }

    ExecuteDatasetSqlToolResult toToolResult(ExecuteDatasetSqlResult result) {
        Map<String, Object> rowSchema = buildRowSchema(result.columns(), result.rows());
        String resultFile = result.rowCount() < TOOL_PREVIEW_ROW_LIMIT
                ? ""
                : datasetResultFileService.persist(result, rowSchema);
        boolean runtimeContextAvailable = datasetResultFileService.hasRuntimeContext();
        boolean rowsTruncated = runtimeContextAvailable && result.rows().size() > TOOL_PREVIEW_ROW_LIMIT;
        List<Map<String, Object>> previewRows =
                rowsTruncated ? result.rows().subList(0, TOOL_PREVIEW_ROW_LIMIT) : result.rows();
        String nextActionHint;
        if (StringUtils.hasText(resultFile)) {
            nextActionHint =
                    "完整查询结果已写入 resultFile，rowSchema 描述每行字段结构。resultFile 的 JSON 顶层是对象，固定结构包含 columns、rowSchema、rowCount、rows；Python 读取完整数据必须使用 json.load(f)[\"rows\"]。需要 Python 二次处理或生成文件时，只生成读取 resultFile 的最小脚本，并通过 run_python args 传入文件路径；禁止使用 file_write 重写查询数据或把数据嵌入脚本。";
        } else if (runtimeContextAvailable) {
            nextActionHint = result.rowCount() < TOOL_PREVIEW_ROW_LIMIT
                    ? "查询结果少于 3 条，已直接返回全部数据，无需读取 JSON 文件。"
                    : "完整查询结果写入 runtime 文件失败，当前仅返回 previewRows。请缩小查询范围后重试，禁止使用 file_write 重写查询数据。";
        } else {
            nextActionHint = "";
        }
        return new ExecuteDatasetSqlToolResult(
                true,
                result.datasetId(),
                result.datasetName(),
                result.sql(),
                result.columns(),
                rowSchema,
                List.copyOf(previewRows),
                result.rowCount(),
                resultFile,
                rowsTruncated,
                nextActionHint);
    }

    private Map<String, Object> buildRowSchema(List<String> columns, List<Map<String, Object>> rows) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String column : columns) {
            properties.put(column, buildColumnSchema(column, rows));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(columns));
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> buildColumnSchema(String column, List<Map<String, Object>> rows) {
        Set<String> types = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            types.add(resolveJsonType(row.get(column)));
        }
        if (types.contains("integer") && types.contains("number")) {
            types.remove("integer");
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        if (!types.isEmpty()) {
            schema.put("type", types.size() == 1 ? types.iterator().next() : List.copyOf(types));
        }
        return schema;
    }

    private String resolveJsonType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof java.math.BigInteger) {
            return "integer";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof Collection<?> || value.getClass().isArray()) {
            return "array";
        }
        if (value instanceof java.util.Date || value instanceof TemporalAccessor) {
            return "string";
        }
        return "string";
    }

    private ExecuteDatasetSqlResult executeDatasetSql(ResolvedDataset resolved, ExecuteDatasetSqlRequest request)
            throws TaskException {
        IntegrationDatasetService.DatasetDetail detail = resolved.detail();
        String normalizedSql = normalizeSql(request == null ? "" : request.sql());
        String executableSql = ensureLimit(normalizedSql, request == null ? null : request.limit());
        if ("LOWCODE_APP".equalsIgnoreCase(trimText(detail.sourceKind()))) {
            return lowcodeDatasetSqlExecutor.execute(detail, executableSql);
        }
        if (!"AI_SOURCE".equalsIgnoreCase(trimText(detail.sourceKind()))) {
            throw new TaskException("当前仅支持 AI_SOURCE 或 LOWCODE_APP 数据集执行 SQL", TaskException.Code.UNKNOWN);
        }
        if (detail.aiDataSourceId() == null) {
            throw new TaskException("数据集未绑定 AI 数据源", TaskException.Code.UNKNOWN);
        }
        Set<String> referencedObjects = extractReferencedObjects(normalizedSql);
        if (referencedObjects.isEmpty()) {
            throw new TaskException("SQL 中未识别到可访问对象，请至少包含 FROM 或 JOIN", TaskException.Code.UNKNOWN);
        }
        Set<String> allowedObjects = detail.objectBindings().stream()
                .filter(item -> isSelected(item.selected()))
                .map(IntegrationDatasetService.ObjectBindingView::objectCode)
                .map(this::normalizeCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String objectCode : referencedObjects) {
            if (!allowedObjects.contains(objectCode)) {
                throw new TaskException("SQL 引用了未授权对象：" + objectCode, TaskException.Code.UNKNOWN);
            }
        }
        IntegrationDataSource dataSource = integrationDataSourceMapper.selectById(detail.aiDataSourceId());
        if (dataSource == null) {
            throw new TaskException("数据源不存在：" + detail.aiDataSourceId(), TaskException.Code.UNKNOWN);
        }
        try (Connection connection = openConnection(dataSource);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(executableSql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int index = 1; index <= metaData.getColumnCount(); index++) {
                columns.add(
                        firstNonBlank(metaData.getColumnLabel(index), metaData.getColumnName(index), "col_" + index));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 1; index <= metaData.getColumnCount(); index++) {
                    row.put(columns.get(index - 1), resultSet.getObject(index));
                }
                rows.add(row);
            }
            return new ExecuteDatasetSqlResult(detail.id(), detail.name(), executableSql, columns, rows, rows.size());
        } catch (Exception ex) {
            throw new TaskException("执行数据集 SQL 失败：" + safeMessage(ex), TaskException.Code.UNKNOWN, ex);
        }
    }

    private ToolFailureResult buildSqlFailureResult(
            ResolvedDataset resolved, ExecuteDatasetSqlRequest request, TaskException ex) {
        IntegrationDatasetService.DatasetDetail detail = resolved == null ? null : resolved.detail();
        String rawSql = trimText(request == null ? "" : request.sql());
        Set<String> referencedObjectCodes = safeExtractReferencedObjects(rawSql);
        String unknownColumnName = extractUnknownColumnName(safeMessage(ex));
        List<ObjectSummary> candidateObjects =
                detail == null ? List.of() : resolveCandidateObjects(detail, rawSql, referencedObjectCodes);
        List<ObjectSchema> schemaHints =
                detail == null ? List.of() : resolveSchemaHints(detail, referencedObjectCodes, candidateObjects);
        List<FieldCandidate> fieldCandidates = resolveFieldCandidates(schemaHints, unknownColumnName);
        return buildToolFailureResult(
                "EXECUTE_DATASET_SQL",
                detail,
                ex,
                new ArrayList<>(referencedObjectCodes),
                candidateObjects,
                schemaHints,
                rawSql,
                buildSuggestedSchemaRequest(referencedObjectCodes, candidateObjects),
                unknownColumnName,
                fieldCandidates);
    }

    private ToolFailureResult buildToolFailureResult(
            String action,
            IntegrationDatasetService.DatasetDetail detail,
            TaskException ex,
            List<String> referencedObjectCodes,
            List<ObjectSummary> candidateObjects,
            List<ObjectSchema> schemaHints,
            String sql,
            SuggestedSchemaRequest suggestedSchemaRequest,
            String unknownColumnName,
            List<FieldCandidate> fieldCandidates) {
        String errorMessage = safeMessage(ex);
        String errorType = inferDatasetErrorType(errorMessage);
        return new ToolFailureResult(
                false,
                action,
                errorType,
                errorMessage,
                buildNextActionHint(action, errorType, errorMessage, referencedObjectCodes),
                unknownColumnName,
                detail == null ? null : detail.id(),
                detail == null ? "" : trimText(detail.name()),
                detail == null ? "" : trimText(detail.sourceKind()),
                List.copyOf(referencedObjectCodes == null ? List.of() : referencedObjectCodes),
                List.copyOf(candidateObjects == null ? List.of() : candidateObjects),
                List.copyOf(schemaHints == null ? List.of() : schemaHints),
                trimText(sql),
                suggestedSchemaRequest,
                List.copyOf(fieldCandidates == null ? List.of() : fieldCandidates));
    }

    private String buildNextActionHint(String action, String errorType, List<String> referencedObjectCodes) {
        String objectText = referencedObjectCodes == null || referencedObjectCodes.isEmpty()
                ? ""
                : "，建议优先查看对象 " + String.join("、", referencedObjectCodes) + " 的结构";
        if ("EXECUTE_DATASET_SQL".equals(action)) {
            return switch (errorType) {
                case "UNKNOWN_COLUMN" -> "当前 SQL 使用了不存在的字段。请先调用 get_dataset_schema 确认字段名" + objectText + "，再重写 SQL。";
                case "UNAUTHORIZED_OBJECT" -> "当前 SQL 引用了未授权对象。请先调用 search_dataset_summary 确认可用对象，再使用 objectCode 重写 SQL。";
                case "SQL_OBJECT_NOT_FOUND" -> "当前 SQL 未识别出可访问对象。请先调用 search_dataset_summary 找到候选对象，再按 objectCode 编写 SQL。";
                case "LOWCODE_SQL_NOT_SUPPORTED" ->
                    "请将 SQL 重写为以 SELECT 开头的简单只读查询，不要包含独立的 update、insert、delete 写操作关键字；字段名如 IsDelete 不会触发此错误。";
                default -> "请不要直接放弃。先调用 search_dataset_summary 或 get_dataset_schema 确认对象和字段，再修正 SQL。";
            };
        }
        if ("GET_DATASET_SCHEMA".equals(action)) {
            return "请先调用 search_dataset_summary 确认候选对象，再按 objectCode 或对象名称重新请求结构。";
        }
        return "请根据错误信息调整工具参数后重试。";
    }

    private String buildNextActionHint(
            String action, String errorType, String errorMessage, List<String> referencedObjectCodes) {
        String hint = buildNextActionHint(action, errorType, referencedObjectCodes);
        if (!"UNKNOWN_COLUMN".equals(errorType)) {
            return hint;
        }
        String unknownColumnName = extractUnknownColumnName(errorMessage);
        if (!StringUtils.hasText(unknownColumnName)) {
            return hint;
        }
        return "字段 `" + unknownColumnName + "` 不存在。" + hint.replaceFirst("^当前 SQL 使用了不存在的字段。", "");
    }

    private SuggestedSchemaRequest buildSuggestedSchemaRequest(
            Set<String> referencedObjectCodes, List<ObjectSummary> candidateObjects) {
        List<String> objectCodes = new ArrayList<>();
        if (referencedObjectCodes != null) {
            for (String objectCode : referencedObjectCodes) {
                if (StringUtils.hasText(objectCode) && !objectCodes.contains(objectCode)) {
                    objectCodes.add(objectCode);
                }
            }
        }
        if (objectCodes.isEmpty() && candidateObjects != null) {
            for (ObjectSummary candidateObject : candidateObjects) {
                String objectCode = normalizeCode(candidateObject.objectCode());
                if (StringUtils.hasText(objectCode) && !objectCodes.contains(objectCode)) {
                    objectCodes.add(objectCode);
                }
            }
        }
        return objectCodes.isEmpty() ? null : new SuggestedSchemaRequest(List.copyOf(objectCodes));
    }

    private List<FieldCandidate> resolveFieldCandidates(List<ObjectSchema> schemaHints, String unknownColumnName) {
        if (!StringUtils.hasText(unknownColumnName) || schemaHints == null || schemaHints.isEmpty()) {
            return List.of();
        }
        String normalizedUnknownColumn = normalizeSearchText(unknownColumnName);
        List<FieldCandidateScore> scoredCandidates = new ArrayList<>();
        for (ObjectSchema objectSchema : schemaHints) {
            collectFieldCandidates(
                    scoredCandidates,
                    objectSchema.objectCode(),
                    objectSchema.objectName(),
                    objectSchema.fields(),
                    normalizedUnknownColumn);
            if (objectSchema.subObjects() != null) {
                for (SubObjectSchema subObject : objectSchema.subObjects()) {
                    collectFieldCandidates(
                            scoredCandidates,
                            subObject.objectCode(),
                            subObject.objectName(),
                            subObject.fields(),
                            normalizedUnknownColumn);
                }
            }
        }
        return scoredCandidates.stream()
                .sorted(Comparator.comparingInt(FieldCandidateScore::score).reversed())
                .map(FieldCandidateScore::candidate)
                .distinct()
                .limit(8)
                .toList();
    }

    private void collectFieldCandidates(
            List<FieldCandidateScore> target,
            String objectCode,
            String objectName,
            List<FieldSchema> fields,
            String normalizedUnknownColumn) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        for (FieldSchema field : fields) {
            int score = scoreFieldCandidate(field, normalizedUnknownColumn);
            if (score <= 0) {
                continue;
            }
            target.add(new FieldCandidateScore(
                    score,
                    new FieldCandidate(
                            trimText(objectCode),
                            firstNonBlank(objectName, objectCode),
                            trimText(field.fieldName()),
                            firstNonBlank(field.fieldLabel(), field.fieldName()))));
        }
        if (!target.isEmpty()) {
            return;
        }
        for (FieldSchema field : fields.stream().limit(5).toList()) {
            target.add(new FieldCandidateScore(
                    1,
                    new FieldCandidate(
                            trimText(objectCode),
                            firstNonBlank(objectName, objectCode),
                            trimText(field.fieldName()),
                            firstNonBlank(field.fieldLabel(), field.fieldName()))));
        }
    }

    private int scoreFieldCandidate(FieldSchema field, String normalizedUnknownColumn) {
        if (field == null || !StringUtils.hasText(normalizedUnknownColumn)) {
            return 0;
        }
        String normalizedFieldName = normalizeSearchText(field.fieldName());
        String normalizedFieldLabel = normalizeSearchText(field.fieldLabel());
        int score = 0;
        if (normalizedUnknownColumn.equals(normalizedFieldName)) {
            score = Math.max(score, 100);
        }
        if (normalizedUnknownColumn.equals(normalizedFieldLabel)) {
            score = Math.max(score, 95);
        }
        if (StringUtils.hasText(normalizedFieldName) && normalizedFieldName.contains(normalizedUnknownColumn)) {
            score = Math.max(score, 80);
        }
        if (StringUtils.hasText(normalizedFieldLabel) && normalizedFieldLabel.contains(normalizedUnknownColumn)) {
            score = Math.max(score, 70);
        }
        if (StringUtils.hasText(normalizedFieldName) && normalizedUnknownColumn.contains(normalizedFieldName)) {
            score = Math.max(score, 60);
        }
        if (StringUtils.hasText(normalizedFieldLabel) && normalizedUnknownColumn.contains(normalizedFieldLabel)) {
            score = Math.max(score, 50);
        }
        return score;
    }

    private String normalizeSearchText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return trimText(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+", "");
    }

    private String extractUnknownColumnName(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return "";
        }
        Matcher matcher = UNKNOWN_COLUMN_PATTERN.matcher(errorMessage);
        return matcher.find() ? trimText(matcher.group(1)) : "";
    }

    private String inferDatasetErrorType(String errorMessage) {
        String normalized = trimText(errorMessage).toLowerCase(Locale.ROOT);
        if (normalized.contains("unknown column")) {
            return "UNKNOWN_COLUMN";
        }
        if (normalized.contains("未授权对象")) {
            return "UNAUTHORIZED_OBJECT";
        }
        if (normalized.contains("未识别到可访问对象")) {
            return "SQL_OBJECT_NOT_FOUND";
        }
        if (normalized.contains("低代码 sqlselect 兼容性校验失败")) {
            return "LOWCODE_SQL_NOT_SUPPORTED";
        }
        if (normalized.contains("仅支持 select / with")) {
            return "UNSUPPORTED_SQL_TYPE";
        }
        if (normalized.contains("仅支持单条查询")) {
            return "MULTI_STATEMENT_NOT_ALLOWED";
        }
        return "DATASET_TOOL_ERROR";
    }

    private Set<String> safeExtractReferencedObjects(String sql) {
        if (!StringUtils.hasText(sql)) {
            return Set.of();
        }
        try {
            return extractReferencedObjects(sql);
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private List<ObjectSummary> resolveCandidateObjects(
            IntegrationDatasetService.DatasetDetail detail, String rawSql, Set<String> referencedObjectCodes) {
        if (detail == null) {
            return List.of();
        }
        Map<String, List<IntegrationDatasetService.FieldBindingView>> fieldsByObject =
                groupFields(detail.fieldBindings());
        if (referencedObjectCodes != null && !referencedObjectCodes.isEmpty()) {
            List<ObjectSummary> matchedObjects = detail.objectBindings().stream()
                    .filter(item -> isSelected(item.selected()))
                    .filter(item -> referencedObjectCodes.contains(normalizeCode(item.objectCode())))
                    .map(item -> buildObjectSummary(item, fieldsByObject.getOrDefault(item.objectCode(), List.of())))
                    .toList();
            if (!matchedObjects.isEmpty()) {
                return matchedObjects;
            }
        }
        String rankingQuestion = StringUtils.hasText(rawSql) ? rawSql : detail.businessLogic();
        return rankObjects(detail, rankingQuestion).stream()
                .limit(3)
                .map(ObjectScore::summary)
                .toList();
    }

    private List<ObjectSchema> resolveSchemaHints(
            IntegrationDatasetService.DatasetDetail detail,
            Set<String> referencedObjectCodes,
            List<ObjectSummary> candidateObjects) {
        if (detail == null) {
            return List.of();
        }
        Set<String> targetCodes = new LinkedHashSet<>();
        if (referencedObjectCodes != null) {
            targetCodes.addAll(referencedObjectCodes);
        }
        if (targetCodes.isEmpty() && candidateObjects != null) {
            for (ObjectSummary candidateObject : candidateObjects) {
                targetCodes.add(normalizeCode(candidateObject.objectCode()));
            }
        }
        return detail.objectBindings().stream()
                .filter(item -> isSelected(item.selected()))
                .filter(item -> targetCodes.isEmpty() || targetCodes.contains(normalizeCode(item.objectCode())))
                .limit(3)
                .map(item -> toObjectSchema(detail, item))
                .toList();
    }

    private ResolvedDataset resolveDataset(String toolName) throws TaskException {
        if (!StringUtils.hasText(toolName)) {
            throw new TaskException("toolName 不能为空", TaskException.Code.UNKNOWN);
        }
        ToolCatalog catalog = toolCatalogMapper.selectByToolName(toolName.trim());
        if (catalog == null
                || !StringUtils.hasText(catalog.getSource())
                || !catalog.getSource().startsWith("dataset:")) {
            throw new TaskException("未找到对应的数据集工具：" + toolName, TaskException.Code.UNKNOWN);
        }
        String datasetCode = catalog.getSource().substring("dataset:".length()).trim();
        if (!StringUtils.hasText(datasetCode)) {
            throw new TaskException("数据集工具来源无效：" + catalog.getSource(), TaskException.Code.UNKNOWN);
        }
        return new ResolvedDataset(catalog, integrationDatasetService.getDatasetByCode(datasetCode));
    }

    private List<ObjectScore> rankObjects(IntegrationDatasetService.DatasetDetail detail, String question) {
        Map<String, List<IntegrationDatasetService.FieldBindingView>> fieldsByObject =
                groupFields(detail.fieldBindings());
        List<ObjectScore> scores = new ArrayList<>();
        for (IntegrationDatasetService.ObjectBindingView objectBinding : detail.objectBindings()) {
            if (!isSelected(objectBinding.selected())) {
                continue;
            }
            int score = calculateObjectScore(
                    objectBinding, fieldsByObject.getOrDefault(objectBinding.objectCode(), List.of()), question);
            scores.add(new ObjectScore(
                    score,
                    buildObjectSummary(
                            objectBinding, fieldsByObject.getOrDefault(objectBinding.objectCode(), List.of()))));
        }
        scores.sort((left, right) -> Integer.compare(right.score(), left.score()));
        return scores;
    }

    private int calculateObjectScore(
            IntegrationDatasetService.ObjectBindingView objectBinding,
            List<IntegrationDatasetService.FieldBindingView> fields,
            String question) {
        if (!StringUtils.hasText(question)) {
            return 1;
        }
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        int score = 0;
        if (normalizedQuestion.contains(trimText(objectBinding.objectName()).toLowerCase(Locale.ROOT))) {
            score += 8;
        }
        if (normalizedQuestion.contains(trimText(objectBinding.objectCode()).toLowerCase(Locale.ROOT))) {
            score += 6;
        }
        for (IntegrationDatasetService.FieldBindingView field : fields) {
            if (normalizedQuestion.contains(trimText(field.fieldAlias()).toLowerCase(Locale.ROOT))) {
                score += 4;
            }
            if (normalizedQuestion.contains(trimText(field.fieldName()).toLowerCase(Locale.ROOT))) {
                score += 2;
            }
        }
        return Math.max(score, 1);
    }

    private ObjectSummary buildObjectSummary(
            IntegrationDatasetService.ObjectBindingView objectBinding,
            List<IntegrationDatasetService.FieldBindingView> fields) {
        List<String> fieldNames = fields.stream()
                .filter(item -> isSelected(item.selected()))
                .map(item -> firstNonBlank(item.fieldAlias(), item.fieldName()))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(8)
                .toList();
        return new ObjectSummary(
                objectBinding.objectCode(), objectBinding.objectName(), objectBinding.objectSource(), fieldNames);
    }

    private String buildDatasetSummary(IntegrationDatasetService.DatasetDetail detail) {
        if (StringUtils.hasText(detail.description())) {
            return detail.description().trim();
        }
        if (StringUtils.hasText(detail.businessLogic())) {
            String text = detail.businessLogic().trim();
            int lineBreak = text.indexOf('\n');
            return lineBreak >= 0 ? text.substring(0, lineBreak).trim() : text;
        }
        return "数据集“" + detail.name() + "”用于提供对象摘要检索、结构查询和查询执行能力。";
    }

    private String buildRelationDescription(
            IntegrationDatasetService.DatasetDetail detail, List<ObjectSummary> candidateObjects) {
        if (StringUtils.hasText(detail.businessLogic())) {
            return detail.businessLogic().trim();
        }
        String objectText = candidateObjects.stream()
                .map(item -> item.objectName() + "（" + item.objectCode() + "）")
                .collect(java.util.stream.Collectors.joining("、"));
        if ("LOWCODE_APP".equalsIgnoreCase(trimText(detail.sourceKind()))) {
            return "该数据集主要包含以下低代码对象：" + objectText
                    + "。其中 objectCode 才是 SQL 中应使用的真实表名，objectName 仅用于中文说明。若存在子表字段，系统会按主表与子表结构理解字段归属。";
        }
        return "该数据集主要包含以下对象：" + objectText
                + "。其中 objectCode 才是 SQL 中应使用的真实表名，objectName 仅用于中文说明。生成 SQL 时请优先从这些对象中选择候选表。";
    }

    private ObjectSchema toObjectSchema(
            IntegrationDatasetService.DatasetDetail detail, IntegrationDatasetService.ObjectBindingView objectBinding) {
        List<IntegrationDatasetService.FieldBindingView> allFields =
                groupFields(detail.fieldBindings()).getOrDefault(objectBinding.objectCode(), List.of());
        List<FieldSchema> mainFields = new ArrayList<>();
        Map<String, SubObjectSchemaBuilder> subtableMap = new LinkedHashMap<>();
        for (IntegrationDatasetService.FieldBindingView field : allFields) {
            if (!isSelected(field.selected())) {
                continue;
            }
            FieldSchema schema = buildFieldSchema(detail, field);
            if (StringUtils.hasText(field.subObjectCode())) {
                String key = trimText(field.subObjectCode());
                SubObjectSchemaBuilder builder = subtableMap.computeIfAbsent(
                        key,
                        ignored -> new SubObjectSchemaBuilder(
                                key, firstNonBlank(field.subObjectName(), field.subObjectCode()), new ArrayList<>()));
                builder.fields().add(schema);
            } else {
                mainFields.add(schema);
            }
        }
        return new ObjectSchema(
                objectBinding.objectCode(),
                objectBinding.objectName(),
                objectBinding.objectSource(),
                mainFields,
                subtableMap.values().stream()
                        .map(item ->
                                new SubObjectSchema(item.objectCode(), item.objectName(), List.copyOf(item.fields())))
                        .toList());
    }

    private Map<String, List<IntegrationDatasetService.FieldBindingView>> groupFields(
            List<IntegrationDatasetService.FieldBindingView> fieldBindings) {
        Map<String, List<IntegrationDatasetService.FieldBindingView>> grouped = new LinkedHashMap<>();
        for (IntegrationDatasetService.FieldBindingView field : fieldBindings) {
            grouped.computeIfAbsent(field.objectCode(), ignored -> new ArrayList<>())
                    .add(field);
        }
        return grouped;
    }

    private boolean matchesObjectFilter(
            IntegrationDatasetService.ObjectBindingView objectBinding,
            Set<String> objectCodes,
            Set<String> objectNames) {
        if (objectCodes.isEmpty() && objectNames.isEmpty()) {
            return true;
        }
        return objectCodes.contains(normalizeCode(objectBinding.objectCode()))
                || objectNames.contains(normalizeCode(objectBinding.objectName()));
    }

    private FieldSchema buildFieldSchema(
            IntegrationDatasetService.DatasetDetail detail, IntegrationDatasetService.FieldBindingView field) {
        String fieldName = field.fieldName();
        String fieldLabel = firstNonBlank(field.fieldAlias(), field.fieldName());
        if ("LOWCODE_APP".equalsIgnoreCase(trimText(detail == null ? null : detail.sourceKind()))) {
            return new FieldSchema(fieldName, fieldLabel, "", "", "", "");
        }
        return new FieldSchema(
                fieldName,
                fieldLabel,
                field.fieldType(),
                field.fieldScope(),
                field.subObjectCode(),
                field.subObjectName());
    }

    private Set<String> extractReferencedObjects(String sql) {
        Set<String> objectCodes = new LinkedHashSet<>();
        Matcher matcher = FROM_JOIN_PATTERN.matcher(sql);
        while (matcher.find()) {
            String raw = trimText(matcher.group(1));
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String cleaned = raw.replace("`", "").replace("\"", "");
            int dotIndex = cleaned.lastIndexOf('.');
            if (dotIndex >= 0) {
                cleaned = cleaned.substring(dotIndex + 1);
            }
            objectCodes.add(normalizeCode(cleaned));
        }
        return objectCodes;
    }

    private String normalizeSql(String sql) throws TaskException {
        String normalized = trimText(sql);
        if (!StringUtils.hasText(normalized)) {
            throw new TaskException("SQL 不能为空", TaskException.Code.UNKNOWN);
        }
        if (normalized.contains(";")) {
            normalized = normalized.replaceAll(";+$", "").trim();
            if (normalized.contains(";")) {
                throw new TaskException("仅支持单条查询 SQL", TaskException.Code.UNKNOWN);
            }
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            throw new TaskException("仅支持 SELECT / WITH 查询 SQL", TaskException.Code.UNKNOWN);
        }
        for (String keyword : FORBIDDEN_SQL_KEYWORDS) {
            if (containsForbiddenSqlKeyword(lower, keyword)) {
                throw new TaskException("检测到不允许的 SQL 关键字：" + keyword, TaskException.Code.UNKNOWN);
            }
        }
        return normalized;
    }

    private boolean containsForbiddenSqlKeyword(String sql, String keyword) {
        if (!StringUtils.hasText(sql) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return Arrays.stream(sql.split("[^a-z0-9_]+"))
                .filter(StringUtils::hasText)
                .anyMatch(token -> token.equals(keyword));
    }

    private String ensureLimit(String sql, Integer requestLimit) {
        if (LIMIT_PATTERN.matcher(sql).find()) {
            return sql;
        }
        int limit = requestLimit == null ? DEFAULT_LIMIT : Math.min(Math.max(requestLimit, 1), MAX_LIMIT);
        return sql + " LIMIT " + limit;
    }

    private Connection openConnection(IntegrationDataSource dataSource) throws Exception {
        ParsedAuthConfig authConfig = parseAuthConfig(dataSource.getAuthConfigJson());
        if ("USERNAME_PASSWORD".equalsIgnoreCase(trimText(dataSource.getAuthType()))) {
            return DriverManager.getConnection(
                    dataSource.getConnectionUri(), authConfig.username(), authConfig.password());
        }
        return DriverManager.getConnection(dataSource.getConnectionUri());
    }

    private ParsedAuthConfig parseAuthConfig(String authConfigJson) {
        if (!StringUtils.hasText(authConfigJson)) {
            return new ParsedAuthConfig("", "");
        }
        try {
            Map<String, String> payload = JSON.readValue(authConfigJson, new TypeReference<Map<String, String>>() {});
            return new ParsedAuthConfig(trimText(payload.get("username")), trimText(payload.get("password")));
        } catch (Exception ex) {
            return new ParsedAuthConfig("", "");
        }
    }

    private Set<String> normalizeCodeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .map(this::normalizeCode)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeCode(String value) {
        return trimText(value).toLowerCase(Locale.ROOT);
    }

    private int normalizeMaxObjects(Integer maxObjects) {
        if (maxObjects == null) {
            return 6;
        }
        return Math.min(Math.max(maxObjects, 1), 12);
    }

    private boolean isSelected(Integer selected) {
        return selected == null || selected == 1;
    }

    private String trimText(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safeMessage(Throwable throwable) {
        return throwable == null
                ? ""
                : firstNonBlank(throwable.getMessage(), throwable.getClass().getSimpleName());
    }

    private record ResolvedDataset(ToolCatalog toolCatalog, IntegrationDatasetService.DatasetDetail detail) {}

    private record ParsedAuthConfig(String username, String password) {}

    private record ObjectScore(int score, ObjectSummary summary) {}

    private record SubObjectSchemaBuilder(String objectCode, String objectName, List<FieldSchema> fields) {}

    public record SearchDatasetSummaryRequest(String question, Integer maxObjects) {}

    public record SearchDatasetSummaryResult(
            Long datasetId,
            String datasetName,
            String sourceKind,
            String summary,
            String relationDescription,
            List<ObjectSummary> candidateObjects) {}

    public record GetDatasetSchemaRequest(List<String> objectCodes, List<String> objectNames) {}

    public record DatasetSchemaResult(
            Long datasetId, String datasetName, String sourceKind, List<ObjectSchema> objects) {}

    public record ObjectSummary(String objectCode, String objectName, String objectSource, List<String> fields) {}

    public record ObjectSchema(
            String objectCode,
            String objectName,
            String objectSource,
            List<FieldSchema> fields,
            List<SubObjectSchema> subObjects) {}

    public record SubObjectSchema(String objectCode, String objectName, List<FieldSchema> fields) {}

    public record FieldSchema(
            String fieldName,
            String fieldLabel,
            String fieldType,
            String fieldScope,
            String subObjectCode,
            String subObjectName) {}

    public record ExecuteDatasetSqlRequest(String sql, Integer limit) {}

    public record ExecuteDatasetSqlResult(
            Long datasetId,
            String datasetName,
            String sql,
            List<String> columns,
            List<Map<String, Object>> rows,
            int rowCount) {}

    public record ExecuteDatasetSqlToolResult(
            boolean success,
            Long datasetId,
            String datasetName,
            String sql,
            List<String> columns,
            Map<String, Object> rowSchema,
            List<Map<String, Object>> previewRows,
            int rowCount,
            String resultFile,
            boolean rowsTruncated,
            String nextActionHint) {}

    public record ToolFailureResult(
            boolean success,
            String action,
            String errorType,
            String errorMessage,
            String nextActionHint,
            String unknownColumnName,
            Long datasetId,
            String datasetName,
            String sourceKind,
            List<String> referencedObjectCodes,
            List<ObjectSummary> candidateObjects,
            List<ObjectSchema> schemaHints,
            String sql,
            SuggestedSchemaRequest suggestedSchemaRequest,
            List<FieldCandidate> fieldCandidates) {}

    public record SuggestedSchemaRequest(List<String> objectCodes) {}

    public record FieldCandidate(String objectCode, String objectName, String fieldName, String fieldLabel) {}

    private record FieldCandidateScore(int score, FieldCandidate candidate) {}
}
