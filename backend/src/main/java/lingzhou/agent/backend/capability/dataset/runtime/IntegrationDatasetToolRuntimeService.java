package lingzhou.agent.backend.capability.dataset.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
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

    private static final Pattern FROM_JOIN_PATTERN =
            Pattern.compile("(?i)\\b(?:from|join)\\s+([`\"\\w.]+)");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\blimit\\s+\\d+");
    private static final List<String> FORBIDDEN_SQL_KEYWORDS = List.of(
            "insert",
            "update",
            "delete",
            "drop",
            "alter",
            "truncate",
            "create",
            "grant",
            "revoke",
            "merge",
            "replace");
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ToolCatalogMapper toolCatalogMapper;
    private final IntegrationDatasetService integrationDatasetService;
    private final IntegrationDataSourceMapper integrationDataSourceMapper;
    private final LowcodeDatasetSqlExecutor lowcodeDatasetSqlExecutor;

    public IntegrationDatasetToolRuntimeService(
            ToolCatalogMapper toolCatalogMapper,
            IntegrationDatasetService integrationDatasetService,
            IntegrationDataSourceMapper integrationDataSourceMapper,
            LowcodeDatasetSqlExecutor lowcodeDatasetSqlExecutor) {
        this.toolCatalogMapper = toolCatalogMapper;
        this.integrationDatasetService = integrationDatasetService;
        this.integrationDataSourceMapper = integrationDataSourceMapper;
        this.lowcodeDatasetSqlExecutor = lowcodeDatasetSqlExecutor;
    }

    public SearchDatasetSummaryResult searchDatasetSummary(String toolName, SearchDatasetSummaryRequest request)
            throws TaskException {
        ResolvedDataset resolved = resolveDataset(toolName);
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
                detail.id(),
                detail.name(),
                detail.sourceKind(),
                summary,
                relationDescription,
                candidateObjects);
    }

    public DatasetSchemaResult getDatasetSchema(String toolName, GetDatasetSchemaRequest request) throws TaskException {
        ResolvedDataset resolved = resolveDataset(toolName);
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

    public ExecuteDatasetSqlResult executeDatasetSql(String toolName, ExecuteDatasetSqlRequest request) throws TaskException {
        ResolvedDataset resolved = resolveDataset(toolName);
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
                columns.add(firstNonBlank(metaData.getColumnLabel(index), metaData.getColumnName(index), "col_" + index));
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

    private ResolvedDataset resolveDataset(String toolName) throws TaskException {
        if (!StringUtils.hasText(toolName)) {
            throw new TaskException("toolName 不能为空", TaskException.Code.UNKNOWN);
        }
        ToolCatalog catalog = toolCatalogMapper.selectByToolName(toolName.trim());
        if (catalog == null || !StringUtils.hasText(catalog.getSource()) || !catalog.getSource().startsWith("dataset:")) {
            throw new TaskException("未找到对应的数据集工具：" + toolName, TaskException.Code.UNKNOWN);
        }
        String datasetCode = catalog.getSource().substring("dataset:".length()).trim();
        if (!StringUtils.hasText(datasetCode)) {
            throw new TaskException("数据集工具来源无效：" + catalog.getSource(), TaskException.Code.UNKNOWN);
        }
        return new ResolvedDataset(catalog, integrationDatasetService.getDatasetByCode(datasetCode));
    }

    private List<ObjectScore> rankObjects(IntegrationDatasetService.DatasetDetail detail, String question) {
        Map<String, List<IntegrationDatasetService.FieldBindingView>> fieldsByObject = groupFields(detail.fieldBindings());
        List<ObjectScore> scores = new ArrayList<>();
        for (IntegrationDatasetService.ObjectBindingView objectBinding : detail.objectBindings()) {
            if (!isSelected(objectBinding.selected())) {
                continue;
            }
            int score = calculateObjectScore(objectBinding, fieldsByObject.getOrDefault(objectBinding.objectCode(), List.of()), question);
            scores.add(new ObjectScore(score, buildObjectSummary(objectBinding, fieldsByObject.getOrDefault(objectBinding.objectCode(), List.of()))));
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
                objectBinding.objectCode(),
                objectBinding.objectName(),
                objectBinding.objectSource(),
                fieldNames);
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
            IntegrationDatasetService.DatasetDetail detail,
            IntegrationDatasetService.ObjectBindingView objectBinding) {
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
                                key,
                                firstNonBlank(field.subObjectName(), field.subObjectCode()),
                                new ArrayList<>()));
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
                        .map(item -> new SubObjectSchema(item.objectCode(), item.objectName(), List.copyOf(item.fields())))
                        .toList());
    }

    private Map<String, List<IntegrationDatasetService.FieldBindingView>> groupFields(
            List<IntegrationDatasetService.FieldBindingView> fieldBindings) {
        Map<String, List<IntegrationDatasetService.FieldBindingView>> grouped = new LinkedHashMap<>();
        for (IntegrationDatasetService.FieldBindingView field : fieldBindings) {
            grouped.computeIfAbsent(field.objectCode(), ignored -> new ArrayList<>()).add(field);
        }
        return grouped;
    }

    private boolean matchesObjectFilter(
            IntegrationDatasetService.ObjectBindingView objectBinding, Set<String> objectCodes, Set<String> objectNames) {
        if (objectCodes.isEmpty() && objectNames.isEmpty()) {
            return true;
        }
        return objectCodes.contains(normalizeCode(objectBinding.objectCode()))
                || objectNames.contains(normalizeCode(objectBinding.objectName()));
    }

    private FieldSchema buildFieldSchema(
            IntegrationDatasetService.DatasetDetail detail,
            IntegrationDatasetService.FieldBindingView field) {
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
            return DriverManager.getConnection(dataSource.getConnectionUri(), authConfig.username(), authConfig.password());
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
        return throwable == null ? "" : firstNonBlank(throwable.getMessage(), throwable.getClass().getSimpleName());
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

    public record DatasetSchemaResult(Long datasetId, String datasetName, String sourceKind, List<ObjectSchema> objects) {}

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
}
