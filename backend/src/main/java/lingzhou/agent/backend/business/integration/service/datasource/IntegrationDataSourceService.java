package lingzhou.agent.backend.business.integration.service.datasource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lingzhou.agent.backend.business.integration.domain.IntegrationDataSource;
import lingzhou.agent.backend.business.integration.mapper.IntegrationDataSourceMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IntegrationDataSourceService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final IntegrationDataSourceMapper integrationDataSourceMapper;
    private final IntegrationDataSourcePermissionService integrationDataSourcePermissionService;

    public IntegrationDataSourceService(
            IntegrationDataSourceMapper integrationDataSourceMapper,
            IntegrationDataSourcePermissionService integrationDataSourcePermissionService) {
        this.integrationDataSourceMapper = integrationDataSourceMapper;
        this.integrationDataSourcePermissionService = integrationDataSourcePermissionService;
    }

    public List<DataSourceSummary> listDataSources(String keyword, String dbType, String status, Long operatorUserId) {
        SysUserModel operator = integrationDataSourcePermissionService.resolveOperator(operatorUserId);
        return integrationDataSourceMapper.search(keyword, dbType, status).stream()
                .filter(item -> integrationDataSourcePermissionService.canViewDataSource(item, operator))
                .map(item -> toSummary(item, operator))
                .toList();
    }

    public DataSourceDetail getDataSource(Long id, Long operatorUserId) throws TaskException {
        IntegrationDataSource dataSource = requireDataSource(id);
        SysUserModel operator = integrationDataSourcePermissionService.resolveOperator(operatorUserId);
        integrationDataSourcePermissionService.assertCanViewDataSource(dataSource, operator);
        return toDetail(dataSource, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceDetail create(CreateOrUpdateDataSourceRequest request, Long operatorUserId) throws TaskException {
        NormalizedDataSource normalized = normalizeRequest(request, null);
        if (integrationDataSourceMapper.selectByName(normalized.name()) != null) {
            throw new TaskException("数据源名称已存在：" + normalized.name(), TaskException.Code.UNKNOWN);
        }
        IntegrationDataSource entity = new IntegrationDataSource();
        entity.setOwnerUserId(operatorUserId);
        applyNormalized(entity, normalized);
        integrationDataSourceMapper.insert(entity);
        return toDetail(entity, integrationDataSourcePermissionService.resolveOperator(operatorUserId));
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceDetail update(Long id, CreateOrUpdateDataSourceRequest request, Long operatorUserId)
            throws TaskException {
        IntegrationDataSource existing = requireDataSource(id);
        SysUserModel operator = integrationDataSourcePermissionService.resolveOperator(operatorUserId);
        integrationDataSourcePermissionService.assertCanOperateDataSource(existing, operator);
        if (request != null && request.permissionScope() != null) {
            int normalizedRequestScope = normalizePermissionScope(request.permissionScope());
            int existingScope = normalizePermissionScope(existing.getPermissionScope());
            if (normalizedRequestScope != existingScope) {
                integrationDataSourcePermissionService.assertCanChangePermissionScope(existing, operator);
            }
        }
        NormalizedDataSource normalized = normalizeRequest(request, existing);
        IntegrationDataSource sameName = integrationDataSourceMapper.selectByName(normalized.name());
        if (sameName != null && !Objects.equals(sameName.getId(), existing.getId())) {
            throw new TaskException("数据源名称已存在：" + normalized.name(), TaskException.Code.UNKNOWN);
        }
        applyNormalized(existing, normalized);
        integrationDataSourceMapper.updateById(existing);
        return toDetail(existing, operator);
    }

    public ConnectionTestResult testConnection(ConnectionTestRequest request, Long operatorUserId)
            throws TaskException {
        IntegrationDataSource existing =
                request == null || request.id() == null ? null : requireDataSource(request.id());
        SysUserModel operator = integrationDataSourcePermissionService.resolveOperator(operatorUserId);
        if (existing != null) {
            integrationDataSourcePermissionService.assertCanOperateDataSource(existing, operator);
        }
        NormalizedDataSource normalized = normalizeRequest(
                new CreateOrUpdateDataSourceRequest(
                        request.name(),
                        request.alias(),
                        request.dbType(),
                        request.connectionUri(),
                        request.authType(),
                        request.username(),
                        request.password(),
                        request.permissionScope(),
                        request.status()),
                existing);
        try (Connection ignored = openConnection(normalized)) {
            return new ConnectionTestResult(true, "连接成功");
        } catch (SQLException ex) {
            throw new TaskException("连接失败：" + safeMessage(ex), TaskException.Code.UNKNOWN, ex);
        }
    }

    public List<ObjectView> listObjects(Long dataSourceId, Long operatorUserId) throws TaskException {
        IntegrationDataSource dataSource = requireDataSource(dataSourceId);
        SysUserModel operator = integrationDataSourcePermissionService.resolveOperator(operatorUserId);
        integrationDataSourcePermissionService.assertCanViewDataSource(dataSource, operator);
        try (Connection connection = openConnection(toNormalized(dataSource))) {
            DatabaseMetaData metaData = connection.getMetaData();
            Map<String, String> tableCommentMap = loadTableComments(connection, dataSource.getDbType());
            List<ObjectView> objects = new ArrayList<>();
            try (ResultSet resultSet =
                    metaData.getTables(connection.getCatalog(), null, "%", new String[] {"TABLE", "VIEW"})) {
                while (resultSet.next()) {
                    String objectName = trimText(resultSet.getString("TABLE_NAME"));
                    if (!StringUtils.hasText(objectName)) {
                        continue;
                    }
                    String schema = trimText(resultSet.getString("TABLE_SCHEM"));
                    String type = trimText(resultSet.getString("TABLE_TYPE"));
                    String remarks = firstNonBlank(
                            trimText(resultSet.getString("REMARKS")),
                            tableCommentMap.get(buildObjectCommentKey(schema, objectName)),
                            tableCommentMap.get(buildObjectCommentKey("", objectName)));
                    objects.add(new ObjectView(
                            objectName, StringUtils.hasText(remarks) ? remarks : objectName, schema, type, remarks));
                }
            }
            return objects.stream()
                    .sorted(Comparator.comparing(ObjectView::objectName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (SQLException ex) {
            throw new TaskException("读取数据源对象失败：" + safeMessage(ex), TaskException.Code.UNKNOWN, ex);
        }
    }

    public List<FieldView> listFields(Long dataSourceId, String objectCode, Long operatorUserId) throws TaskException {
        String normalizedObjectCode = requireText(objectCode, "objectCode 不能为空");
        IntegrationDataSource dataSource = requireDataSource(dataSourceId);
        SysUserModel operator = integrationDataSourcePermissionService.resolveOperator(operatorUserId);
        integrationDataSourcePermissionService.assertCanViewDataSource(dataSource, operator);
        try (Connection connection = openConnection(toNormalized(dataSource))) {
            DatabaseMetaData metaData = connection.getMetaData();
            Map<String, String> columnCommentMap =
                    loadColumnComments(connection, dataSource.getDbType(), normalizedObjectCode);
            List<FieldView> fields = new ArrayList<>();
            try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, normalizedObjectCode, "%")) {
                while (resultSet.next()) {
                    String columnName = trimText(resultSet.getString("COLUMN_NAME"));
                    String remarks = firstNonBlank(
                            trimText(resultSet.getString("REMARKS")),
                            columnCommentMap.get(columnName.toLowerCase(Locale.ROOT)));
                    fields.add(new FieldView(
                            normalizedObjectCode,
                            columnName,
                            trimText(resultSet.getString("TYPE_NAME")),
                            resultSet.getInt("DATA_TYPE"),
                            remarks,
                            "YES".equalsIgnoreCase(resultSet.getString("IS_NULLABLE")),
                            resultSet.getInt("ORDINAL_POSITION")));
                }
            }
            return fields.stream()
                    .sorted(Comparator.comparingInt(FieldView::ordinalPosition))
                    .toList();
        } catch (SQLException ex) {
            throw new TaskException("读取字段失败：" + safeMessage(ex), TaskException.Code.UNKNOWN, ex);
        }
    }

    public List<RelationView> listRelations(Long dataSourceId, List<String> objectCodes, Long operatorUserId)
            throws TaskException {
        IntegrationDataSource dataSource = requireDataSource(dataSourceId);
        SysUserModel operator = integrationDataSourcePermissionService.resolveOperator(operatorUserId);
        integrationDataSourcePermissionService.assertCanViewDataSource(dataSource, operator);
        List<String> normalizedObjectCodes = objectCodes == null
                ? List.of()
                : objectCodes.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList();
        try (Connection connection = openConnection(toNormalized(dataSource))) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<RelationView> relations = new ArrayList<>();
            for (String objectCode : normalizedObjectCodes) {
                try (ResultSet resultSet = metaData.getImportedKeys(connection.getCatalog(), null, objectCode)) {
                    while (resultSet.next()) {
                        String fkTable = trimText(resultSet.getString("FKTABLE_NAME"));
                        String pkTable = trimText(resultSet.getString("PKTABLE_NAME"));
                        if (!normalizedObjectCodes.isEmpty()
                                && !normalizedObjectCodes.contains(fkTable)
                                && !normalizedObjectCodes.contains(pkTable)) {
                            continue;
                        }
                        relations.add(new RelationView(
                                fkTable,
                                trimText(resultSet.getString("FKCOLUMN_NAME")),
                                pkTable,
                                trimText(resultSet.getString("PKCOLUMN_NAME")),
                                "DATABASE_METADATA"));
                    }
                }
            }
            return relations.stream()
                    .distinct()
                    .sorted(Comparator.comparing(RelationView::leftObjectCode)
                            .thenComparing(RelationView::leftFieldName))
                    .toList();
        } catch (SQLException ex) {
            throw new TaskException("读取对象关系失败：" + safeMessage(ex), TaskException.Code.UNKNOWN, ex);
        }
    }

    private IntegrationDataSource requireDataSource(Long id) throws TaskException {
        return integrationDataSourcePermissionService.requireDataSource(id);
    }

    private NormalizedDataSource normalizeRequest(
            CreateOrUpdateDataSourceRequest request, IntegrationDataSource existing) throws TaskException {
        if (request == null) {
            throw new TaskException("请求参数不能为空", TaskException.Code.UNKNOWN);
        }
        String name = requireText(request.name(), "数据源名称不能为空");
        String dbType = normalizeDbType(request.dbType());
        String connectionUri = requireText(request.connectionUri(), "连接字符串不能为空");
        String authType = normalizeAuthType(request.authType());
        String username = trimText(request.username());
        String password = resolvePassword(request.password(), existing);
        if ("USERNAME_PASSWORD".equals(authType)) {
            if (!StringUtils.hasText(username)) {
                throw new TaskException("用户名不能为空", TaskException.Code.UNKNOWN);
            }
            if (!StringUtils.hasText(password)) {
                throw new TaskException("密码不能为空", TaskException.Code.UNKNOWN);
            }
        }
        return new NormalizedDataSource(
                name,
                trimText(request.alias()),
                dbType,
                connectionUri,
                authType,
                username,
                password,
                normalizePermissionScope(
                        request.permissionScope() != null
                                ? request.permissionScope()
                                : (existing == null ? null : existing.getPermissionScope())),
                normalizeStatus(request.status()));
    }

    private String resolvePassword(String rawPassword, IntegrationDataSource existing) throws TaskException {
        if (StringUtils.hasText(rawPassword)) {
            return rawPassword.trim();
        }
        if (existing == null) {
            return "";
        }
        return parseAuthConfig(existing.getAuthConfigJson()).password();
    }

    private void applyNormalized(IntegrationDataSource entity, NormalizedDataSource normalized) throws TaskException {
        entity.setName(normalized.name());
        entity.setAlias(normalized.alias());
        entity.setDbType(normalized.dbType());
        entity.setConnectionUri(normalized.connectionUri());
        entity.setAuthType(normalized.authType());
        entity.setAuthConfigJson(serializeAuthConfig(normalized.username(), normalized.password()));
        entity.setPermissionScope(normalized.permissionScope());
        entity.setStatus(normalized.status());
    }

    private DataSourceSummary toSummary(IntegrationDataSource entity, SysUserModel operator) {
        ParsedAuthConfig authConfig = parseAuthConfig(entity.getAuthConfigJson());
        boolean canOperate = integrationDataSourcePermissionService.canOperateDataSource(entity, operator);
        boolean canChangePermission = integrationDataSourcePermissionService.canChangePermissionScope(entity, operator);
        return new DataSourceSummary(
                entity.getId(),
                entity.getName(),
                entity.getAlias(),
                entity.getDbType(),
                entity.getConnectionUri(),
                entity.getAuthType(),
                authConfig.username(),
                StringUtils.hasText(authConfig.password()),
                entity.getOwnerUserId(),
                normalizePermissionScope(entity.getPermissionScope()),
                canOperate,
                canChangePermission,
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private DataSourceDetail toDetail(IntegrationDataSource entity, SysUserModel operator) {
        DataSourceSummary summary = toSummary(entity, operator);
        return new DataSourceDetail(
                summary.id(),
                summary.name(),
                summary.alias(),
                summary.dbType(),
                summary.connectionUri(),
                summary.authType(),
                summary.username(),
                summary.passwordConfigured(),
                summary.ownerUserId(),
                summary.permissionScope(),
                summary.canOperate(),
                summary.canChangePermission(),
                summary.status(),
                summary.createdAt(),
                summary.updatedAt());
    }

    private Connection openConnection(NormalizedDataSource normalized) throws SQLException {
        if ("USERNAME_PASSWORD".equals(normalized.authType())) {
            return DriverManager.getConnection(
                    normalized.connectionUri(), normalized.username(), normalized.password());
        }
        return DriverManager.getConnection(normalized.connectionUri());
    }

    private NormalizedDataSource toNormalized(IntegrationDataSource entity) throws TaskException {
        ParsedAuthConfig authConfig = parseAuthConfig(entity.getAuthConfigJson());
        return new NormalizedDataSource(
                entity.getName(),
                trimText(entity.getAlias()),
                normalizeDbType(entity.getDbType()),
                requireText(entity.getConnectionUri(), "连接字符串不能为空"),
                normalizeAuthType(entity.getAuthType()),
                authConfig.username(),
                authConfig.password(),
                normalizePermissionScope(entity.getPermissionScope()),
                normalizeStatus(entity.getStatus()));
    }

    private ParsedAuthConfig parseAuthConfig(String authConfigJson) {
        if (!StringUtils.hasText(authConfigJson)) {
            return new ParsedAuthConfig("", "");
        }
        try {
            Map<String, String> values = JSON.readValue(authConfigJson, new TypeReference<Map<String, String>>() {});
            return new ParsedAuthConfig(trimText(values.get("username")), trimText(values.get("password")));
        } catch (Exception ignore) {
            return new ParsedAuthConfig("", "");
        }
    }

    private String serializeAuthConfig(String username, String password) throws TaskException {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("username", trimText(username));
        values.put("password", trimText(password));
        try {
            return JSON.writeValueAsString(values);
        } catch (Exception ex) {
            throw new TaskException("序列化鉴权配置失败", TaskException.Code.UNKNOWN, ex);
        }
    }

    private String normalizeDbType(String dbType) throws TaskException {
        String normalized = requireText(dbType, "数据库类型不能为空").toUpperCase(Locale.ROOT);
        if (!List.of("MYSQL", "POSTGRESQL", "SQLSERVER", "ORACLE").contains(normalized)) {
            throw new TaskException("暂不支持的数据库类型：" + normalized, TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private String normalizeAuthType(String authType) {
        if (!StringUtils.hasText(authType)) {
            return "USERNAME_PASSWORD";
        }
        return authType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "ACTIVE";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private Integer normalizePermissionScope(Integer value) {
        return integrationDataSourcePermissionService.normalizePermissionScope(value);
    }

    private String requireText(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private String trimText(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeMessage(Exception ex) {
        String message = ex == null ? "" : ex.getMessage();
        return StringUtils.hasText(message) ? message.trim() : ex.getClass().getSimpleName();
    }

    private Map<String, String> loadTableComments(Connection connection, String dbType) {
        if (!"MYSQL".equalsIgnoreCase(trimText(dbType))) {
            return Map.of();
        }
        String catalog = trimText(resolveCatalog(connection));
        if (!StringUtils.hasText(catalog)) {
            return Map.of();
        }
        String sql =
                """
                SELECT table_schema, table_name, table_comment
                FROM information_schema.tables
                WHERE table_schema = ?
                """;
        Map<String, String> comments = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, catalog);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String schema = trimText(resultSet.getString("table_schema"));
                    String tableName = trimText(resultSet.getString("table_name"));
                    String comment = trimText(resultSet.getString("table_comment"));
                    if (StringUtils.hasText(tableName) && StringUtils.hasText(comment)) {
                        comments.put(buildObjectCommentKey(schema, tableName), comment);
                    }
                }
            }
        } catch (SQLException ignore) {
            return Map.of();
        }
        return comments;
    }

    private Map<String, String> loadColumnComments(Connection connection, String dbType, String objectCode) {
        if (!"MYSQL".equalsIgnoreCase(trimText(dbType)) || !StringUtils.hasText(objectCode)) {
            return Map.of();
        }
        String catalog = trimText(resolveCatalog(connection));
        if (!StringUtils.hasText(catalog)) {
            return Map.of();
        }
        String sql =
                """
                SELECT column_name, column_comment
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """;
        Map<String, String> comments = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, catalog);
            statement.setString(2, objectCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String columnName = trimText(resultSet.getString("column_name"));
                    String comment = trimText(resultSet.getString("column_comment"));
                    if (StringUtils.hasText(columnName) && StringUtils.hasText(comment)) {
                        comments.put(columnName.toLowerCase(Locale.ROOT), comment);
                    }
                }
            }
        } catch (SQLException ignore) {
            return Map.of();
        }
        return comments;
    }

    private String resolveCatalog(Connection connection) {
        try {
            return trimText(connection.getCatalog());
        } catch (SQLException ignore) {
            return "";
        }
    }

    private String buildObjectCommentKey(String schema, String objectName) {
        return (trimText(schema) + "|" + trimText(objectName)).toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private record NormalizedDataSource(
            String name,
            String alias,
            String dbType,
            String connectionUri,
            String authType,
            String username,
            String password,
            Integer permissionScope,
            String status) {}

    private record ParsedAuthConfig(String username, String password) {}

    public record CreateOrUpdateDataSourceRequest(
            String name,
            String alias,
            String dbType,
            String connectionUri,
            String authType,
            String username,
            String password,
            Integer permissionScope,
            String status) {}

    public record ConnectionTestRequest(
            Long id,
            String name,
            String alias,
            String dbType,
            String connectionUri,
            String authType,
            String username,
            String password,
            Integer permissionScope,
            String status) {}

    public record ConnectionTestResult(boolean success, String message) {}

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long operatorUserId) throws TaskException {
        IntegrationDataSource existing = requireDataSource(id);
        SysUserModel operator = integrationDataSourcePermissionService.resolveOperator(operatorUserId);
        integrationDataSourcePermissionService.assertCanOperateDataSource(existing, operator);
        integrationDataSourceMapper.deleteById(existing.getId());
    }

    public record DataSourceSummary(
            Long id,
            String name,
            String alias,
            String dbType,
            String connectionUri,
            String authType,
            String username,
            boolean passwordConfigured,
            Long ownerUserId,
            Integer permissionScope,
            boolean canOperate,
            boolean canChangePermission,
            String status,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record DataSourceDetail(
            Long id,
            String name,
            String alias,
            String dbType,
            String connectionUri,
            String authType,
            String username,
            boolean passwordConfigured,
            Long ownerUserId,
            Integer permissionScope,
            boolean canOperate,
            boolean canChangePermission,
            String status,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record ObjectView(
            String objectCode, String objectName, String schemaName, String objectType, String comment) {}

    public record FieldView(
            String objectCode,
            String fieldName,
            String fieldType,
            Integer jdbcType,
            String comment,
            boolean nullable,
            int ordinalPosition) {}

    public record RelationView(
            String leftObjectCode,
            String leftFieldName,
            String rightObjectCode,
            String rightFieldName,
            String relationSource) {}
}
