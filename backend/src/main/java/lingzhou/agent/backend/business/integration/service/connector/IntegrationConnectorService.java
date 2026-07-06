package lingzhou.agent.backend.business.integration.service.connector;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lingzhou.agent.backend.business.integration.domain.IntegrationConnector;
import lingzhou.agent.backend.business.integration.domain.IntegrationConnectorApi;
import lingzhou.agent.backend.business.integration.mapper.IntegrationConnectorApiMapper;
import lingzhou.agent.backend.business.integration.mapper.IntegrationConnectorMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillToolBindingMapper;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import lingzhou.agent.backend.capability.api.connector.ConnectorApiExecutor;
import lingzhou.agent.backend.capability.api.publish.ConnectorApiToolPublishService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IntegrationConnectorService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {};
    private static final String API_PUBLISH_STATUS_DRAFT = "DRAFT";
    private static final String API_PUBLISH_STATUS_PUBLISHED = "PUBLISHED";

    private final ObjectMapper objectMapper;
    private final IntegrationConnectorMapper integrationConnectorMapper;
    private final IntegrationConnectorApiMapper integrationConnectorApiMapper;
    private final IntegrationConnectorPermissionService integrationConnectorPermissionService;
    private final ToolCatalogMapper toolCatalogMapper;
    private final SkillToolBindingMapper skillToolBindingMapper;
    private final ConnectorApiToolPublishService connectorApiToolPublishService;
    private final ConnectorApiExecutor connectorApiExecutor;

    public IntegrationConnectorService(
            ObjectMapper objectMapper,
            IntegrationConnectorMapper integrationConnectorMapper,
            IntegrationConnectorApiMapper integrationConnectorApiMapper,
            IntegrationConnectorPermissionService integrationConnectorPermissionService,
            ToolCatalogMapper toolCatalogMapper,
            SkillToolBindingMapper skillToolBindingMapper,
            ConnectorApiToolPublishService connectorApiToolPublishService,
            ConnectorApiExecutor connectorApiExecutor) {
        this.objectMapper = objectMapper;
        this.integrationConnectorMapper = integrationConnectorMapper;
        this.integrationConnectorApiMapper = integrationConnectorApiMapper;
        this.integrationConnectorPermissionService = integrationConnectorPermissionService;
        this.toolCatalogMapper = toolCatalogMapper;
        this.skillToolBindingMapper = skillToolBindingMapper;
        this.connectorApiToolPublishService = connectorApiToolPublishService;
        this.connectorApiExecutor = connectorApiExecutor;
    }

    public ConnectorPageResult listConnectors(
            Integer page, Integer pageSize, String keyword, String status, Long operatorUserId) {
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        int safePage = Math.max(page == null ? 1 : page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize == null ? 10 : pageSize, 1000));
        IPage<IntegrationConnector> pageData = integrationConnectorMapper.searchPage(
                safePage,
                safePageSize,
                keyword,
                normalizeOptionalStatus(status),
                integrationConnectorPermissionService.isAdmin(operator),
                operatorUserId);
        List<ConnectorSummary> items = pageData.getRecords().stream()
                .map(item -> toConnectorSummary(item, operator))
                .toList();
        return new ConnectorPageResult(items, pageData.getTotal(), safePage, safePageSize);
    }

    public ConnectorDetail getConnector(Long id, Long operatorUserId) throws TaskException {
        IntegrationConnector connector = requireConnector(id);
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanViewConnector(connector, operator);
        return toConnectorDetail(connector, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConnectorDetail createConnector(CreateOrUpdateConnectorRequest request, Long operatorUserId)
            throws TaskException {
        NormalizedConnector normalized = normalizeConnectorRequest(request, null);
        IntegrationConnector sameName = integrationConnectorMapper.selectByName(normalized.name());
        if (sameName != null) {
            throw new TaskException("连接器名称已存在: " + normalized.name(), TaskException.Code.UNKNOWN);
        }
        IntegrationConnector sameAlias = integrationConnectorMapper.selectByAlias(normalized.alias());
        if (sameAlias != null) {
            throw new TaskException("连接器编码已存在: " + normalized.alias(), TaskException.Code.UNKNOWN);
        }
        IntegrationConnector connector = new IntegrationConnector();
        connector.setOwnerUserId(operatorUserId);
        applyNormalizedConnector(connector, normalized);
        integrationConnectorMapper.insert(connector);
        return toConnectorDetail(connector, integrationConnectorPermissionService.resolveOperator(operatorUserId));
    }

    @Transactional(rollbackFor = Exception.class)
    public ConnectorDetail updateConnector(Long id, CreateOrUpdateConnectorRequest request, Long operatorUserId)
            throws TaskException {
        IntegrationConnector connector = requireConnector(id);
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanOperateConnector(connector, operator);
        if (request != null && request.permissionScope() != null) {
            int newScope = integrationConnectorPermissionService.normalizePermissionScope(request.permissionScope());
            int oldScope = integrationConnectorPermissionService.normalizePermissionScope(connector.getPermissionScope());
            if (newScope != oldScope) {
                integrationConnectorPermissionService.assertCanChangePermissionScope(connector, operator);
            }
        }
        NormalizedConnector normalized = normalizeConnectorRequest(request, connector);
        IntegrationConnector sameName = integrationConnectorMapper.selectByName(normalized.name());
        if (sameName != null && !Objects.equals(sameName.getId(), connector.getId())) {
            throw new TaskException("连接器名称已存在: " + normalized.name(), TaskException.Code.UNKNOWN);
        }
        IntegrationConnector sameAlias = integrationConnectorMapper.selectByAlias(normalized.alias());
        if (sameAlias != null && !Objects.equals(sameAlias.getId(), connector.getId())) {
            throw new TaskException("连接器编码已存在: " + normalized.alias(), TaskException.Code.UNKNOWN);
        }
        applyNormalizedConnector(connector, normalized);
        integrationConnectorMapper.updateById(connector);
        syncConnectorApis(connector);
        return toConnectorDetail(connector, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConnector(Long id, Long operatorUserId) throws TaskException {
        IntegrationConnector connector = requireConnector(id);
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanOperateConnector(connector, operator);
        List<IntegrationConnectorApi> apis = integrationConnectorApiMapper.selectByConnectorId(connector.getId());
        List<String> toolNames = apis.stream()
                .map(IntegrationConnectorApi::getToolName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (!toolNames.isEmpty()) {
            skillToolBindingMapper.deleteByToolNames(toolNames);
            toolNames.forEach(connectorApiToolPublishService::disable);
        }
        apis.forEach(api -> integrationConnectorApiMapper.deleteById(api.getId()));
        integrationConnectorMapper.deleteById(connector.getId());
    }

    public ConnectorAuthTestResult testConnectorAuth(
            Long connectorId, ConnectorAuthTestRequest request, Long operatorUserId) throws TaskException {
        IntegrationConnector connector = requireConnector(connectorId);
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanOperateConnector(connector, operator);
        Map<String, Object> authItem = resolveAuthItemForTest(connector, request);
        ConnectorApiExecutor.AuthExecutionDebugResult result = connectorApiExecutor.executeAuthenticationForTest(
                connector,
                authItem,
                request == null ? Map.of() : normalizeInputArguments(request.variables()));
        return new ConnectorAuthTestResult(
                result.method(),
                result.requestUrl(),
                result.requestHeaders(),
                result.requestQuery(),
                result.requestBody(),
                result.rawResponse(),
                result.jsonStr(),
                result.returnInfo(),
                result.accessToken(),
                result.expiresInSeconds(),
                result.tokenType(),
                result.message());
    }

    public List<ConnectorApiSummary> listConnectorApis(Long connectorId, Long operatorUserId) throws TaskException {
        IntegrationConnector connector = requireConnector(connectorId);
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanViewConnector(connector, operator);
        return integrationConnectorApiMapper.selectByConnectorId(connectorId).stream()
                .map(api -> toConnectorApiSummary(connector, api, operator))
                .toList();
    }

    public ConnectorCodePreview previewConnectorCode(String connectorName) {
        return new ConnectorCodePreview(generateCodeInitials(connectorName, "connector"));
    }

    public ConnectorApiCodePreview previewConnectorApiCode(Long connectorId, String apiName) throws TaskException {
        IntegrationConnector connector = requireConnector(connectorId);
        return new ConnectorApiCodePreview(generateApiCode(connector, apiName));
    }

    public ConnectorApiDetail getConnectorApi(Long apiId, Long operatorUserId) throws TaskException {
        IntegrationConnectorApi api = requireConnectorApi(apiId);
        IntegrationConnector connector = requireConnector(api.getConnectorId());
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanViewConnector(connector, operator);
        return toConnectorApiDetail(connector, api, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConnectorApiDetail createConnectorApi(
            Long connectorId, CreateOrUpdateConnectorApiRequest request, Long operatorUserId) throws TaskException {
        IntegrationConnector connector = requireConnector(connectorId);
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanOperateConnector(connector, operator);
        NormalizedConnectorApi normalized = normalizeApiRequest(request, connector, null);
        IntegrationConnectorApi sameName = findConnectorApiByName(connectorId, normalized.apiName(), null);
        if (sameName != null) {
            throw new TaskException("API 名称不能重复: " + normalized.apiName(), TaskException.Code.UNKNOWN);
        }
        IntegrationConnectorApi sameCode = findConnectorApiByCode(connectorId, normalized.apiCode(), null);
        if (sameCode != null) {
            throw new TaskException("API 编码不能重复: " + normalized.apiCode(), TaskException.Code.UNKNOWN);
        }
        IntegrationConnectorApi api = new IntegrationConnectorApi();
        api.setConnectorId(connectorId);
        applyNormalizedApi(api, normalized);
        api.setPublishStatus(API_PUBLISH_STATUS_DRAFT);
        api.setPublishedVersion(0);
        api.setPublishedAt(null);
        integrationConnectorApiMapper.insert(api);
        syncApiPublication(connector, api);
        return toConnectorApiDetail(connector, api, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConnectorApiDetail updateConnectorApi(
            Long apiId, CreateOrUpdateConnectorApiRequest request, Long operatorUserId) throws TaskException {
        IntegrationConnectorApi api = requireConnectorApi(apiId);
        IntegrationConnector connector = requireConnector(api.getConnectorId());
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanOperateConnector(connector, operator);
        String oldToolName = text(api.getToolName());
        NormalizedConnectorApi normalized = normalizeApiRequest(request, connector, api);
        IntegrationConnectorApi sameName =
                findConnectorApiByName(connector.getId(), normalized.apiName(), api.getId());
        if (sameName != null) {
            throw new TaskException("API 名称不能重复: " + normalized.apiName(), TaskException.Code.UNKNOWN);
        }
        IntegrationConnectorApi sameCode =
                findConnectorApiByCode(connector.getId(), normalized.apiCode(), api.getId());
        if (sameCode != null) {
            throw new TaskException("API 编码不能重复: " + normalized.apiCode(), TaskException.Code.UNKNOWN);
        }
        applyNormalizedApi(api, normalized);
        integrationConnectorApiMapper.updateById(api);
        if (StringUtils.hasText(oldToolName) && !oldToolName.equals(api.getToolName())) {
            skillToolBindingMapper.deleteByToolNames(List.of(oldToolName));
            connectorApiToolPublishService.disable(oldToolName);
        }
        syncApiPublication(connector, api);
        return toConnectorApiDetail(connector, api, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConnectorApi(Long apiId, Long operatorUserId) throws TaskException {
        IntegrationConnectorApi api = requireConnectorApi(apiId);
        IntegrationConnector connector = requireConnector(api.getConnectorId());
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanOperateConnector(connector, operator);
        if (StringUtils.hasText(api.getToolName())) {
            String toolName = api.getToolName().trim();
            skillToolBindingMapper.deleteByToolNames(List.of(toolName));
            connectorApiToolPublishService.disable(toolName);
        }
        integrationConnectorApiMapper.deleteById(api.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public ConnectorApiDetail publishConnectorApi(Long apiId, Long operatorUserId) throws TaskException {
        IntegrationConnectorApi api = requireConnectorApi(apiId);
        IntegrationConnector connector = requireConnector(api.getConnectorId());
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanOperateConnector(connector, operator);
        api.setPublishStatus(API_PUBLISH_STATUS_PUBLISHED);
        api.setPublishedVersion((api.getPublishedVersion() == null ? 0 : api.getPublishedVersion()) + 1);
        api.setPublishedAt(new Date());
        integrationConnectorApiMapper.updateById(api);
        syncApiPublication(connector, api);
        return toConnectorApiDetail(connector, api, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConnectorApiDetail disableConnectorApi(Long apiId, Long operatorUserId) throws TaskException {
        IntegrationConnectorApi api = requireConnectorApi(apiId);
        IntegrationConnector connector = requireConnector(api.getConnectorId());
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanOperateConnector(connector, operator);
        api.setPublishStatus(API_PUBLISH_STATUS_DRAFT);
        integrationConnectorApiMapper.updateById(api);
        if (StringUtils.hasText(api.getToolName())) {
            String toolName = api.getToolName().trim();
            skillToolBindingMapper.deleteByToolNames(List.of(toolName));
            connectorApiToolPublishService.disable(toolName);
        }
        return toConnectorApiDetail(connector, api, operator);
    }

    public ConnectorApiTestResult testConnectorApi(Long apiId, ConnectorApiTestRequest request, Long operatorUserId)
            throws TaskException {
        IntegrationConnectorApi api = requireConnectorApi(apiId);
        IntegrationConnector connector = requireConnector(api.getConnectorId());
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanOperateConnector(connector, operator);
        ConnectorApiExecutor.ExecutionDebugResult result = connectorApiExecutor.executeForTest(
                connector,
                api,
                request == null ? Map.of() : normalizeInputArguments(request.arguments()));
        return new ConnectorApiTestResult(
                result.method(),
                result.requestUrl(),
                result.requestHeaders(),
                result.requestQuery(),
                result.requestBody(),
                result.rawResponse(),
                result.jsonStr(),
                result.returnInfo(),
                result.result());
    }

    private void syncConnectorApis(IntegrationConnector connector) {
        integrationConnectorApiMapper.selectByConnectorId(connector.getId()).forEach(api -> syncApiPublication(connector, api));
    }

    private void syncApiPublication(IntegrationConnector connector, IntegrationConnectorApi api) {
        if (connector == null || api == null || !StringUtils.hasText(api.getToolName())) {
            return;
        }
        if (isApiPublished(api)
                && "ACTIVE".equalsIgnoreCase(text(connector.getStatus()))
                && api.getEnabled() != null
                && api.getEnabled() == 1) {
            connectorApiToolPublishService.publish(connector, api);
            return;
        }
        connectorApiToolPublishService.disable(api.getToolName().trim());
    }

    private IntegrationConnector requireConnector(Long id) throws TaskException {
        return integrationConnectorPermissionService.requireConnector(id);
    }

    private IntegrationConnectorApi requireConnectorApi(Long apiId) throws TaskException {
        if (apiId == null) {
            throw new TaskException("连接器 API ID 不能为空", TaskException.Code.UNKNOWN);
        }
        IntegrationConnectorApi api = integrationConnectorApiMapper.selectById(apiId);
        if (api == null) {
            throw new TaskException("连接器 API 不存在: " + apiId, TaskException.Code.UNKNOWN);
        }
        return api;
    }

    private IntegrationConnectorApi findConnectorApiByName(Long connectorId, String apiName, Long excludeApiId) {
        String normalizedName = normalizeCompareText(apiName);
        if (!StringUtils.hasText(normalizedName)) {
            return null;
        }
        return integrationConnectorApiMapper.selectByConnectorId(connectorId).stream()
                .filter(item -> !Objects.equals(item.getId(), excludeApiId))
                .filter(item -> normalizedName.equals(normalizeCompareText(item.getApiName())))
                .findFirst()
                .orElse(null);
    }

    private IntegrationConnectorApi findConnectorApiByCode(Long connectorId, String apiCode, Long excludeApiId) {
        String normalizedCode = normalizeCompareText(apiCode);
        if (!StringUtils.hasText(normalizedCode)) {
            return null;
        }
        return integrationConnectorApiMapper.selectByConnectorId(connectorId).stream()
                .filter(item -> !Objects.equals(item.getId(), excludeApiId))
                .filter(item -> normalizedCode.equals(normalizeCompareText(item.getApiCode())))
                .findFirst()
                .orElse(null);
    }

    private ConnectorSummary toConnectorSummary(IntegrationConnector connector, SysUserModel operator) {
        List<Map<String, Object>> authList = parseAuthItems(connector.getAuthConfigJson());
        return new ConnectorSummary(
                connector.getId(),
                connector.getName(),
                connector.getAlias(),
                connector.getBaseUrl(),
                authList.isEmpty() ? "NONE" : "MULTIPLE",
                authList.size(),
                connector.getOwnerUserId(),
                integrationConnectorPermissionService.normalizePermissionScope(connector.getPermissionScope()),
                integrationConnectorPermissionService.canOperateConnector(connector, operator),
                integrationConnectorPermissionService.canChangePermissionScope(connector, operator),
                connector.getStatus(),
                connector.getCreatedAt(),
                connector.getUpdatedAt());
    }

    private ConnectorDetail toConnectorDetail(IntegrationConnector connector, SysUserModel operator) {
        List<ConnectorAuthDetail> authList = parseAuthItems(connector.getAuthConfigJson()).stream()
                .map(this::toConnectorAuthDetail)
                .toList();
        return new ConnectorDetail(
                connector.getId(),
                connector.getName(),
                connector.getAlias(),
                connector.getBaseUrl(),
                authList.isEmpty() ? "NONE" : "MULTIPLE",
                authList,
                connector.getOwnerUserId(),
                integrationConnectorPermissionService.normalizePermissionScope(connector.getPermissionScope()),
                integrationConnectorPermissionService.canOperateConnector(connector, operator),
                integrationConnectorPermissionService.canChangePermissionScope(connector, operator),
                connector.getStatus(),
                connector.getCreatedAt(),
                connector.getUpdatedAt());
    }

    private ConnectorAuthDetail toConnectorAuthDetail(Map<String, Object> item) {
        Map<String, Object> authInfo = normalizeObjectMap(item.get("authInfo"));
        return new ConnectorAuthDetail(
                firstText(item.get("id")),
                firstText(item.get("name")),
                firstText(item.get("remark")),
                firstText(item.get("authType"), "OAUTH2_CLIENT_CREDENTIALS"),
                authInfo,
                normalizeState(item.get("state")));
    }

    private ConnectorApiSummary toConnectorApiSummary(
            IntegrationConnector connector, IntegrationConnectorApi api, SysUserModel operator) {
        return new ConnectorApiSummary(
                api.getId(),
                connector.getId(),
                api.getConnectId(),
                api.getConnectName(),
                api.getApiCode(),
                api.getApiName(),
                api.getDescription(),
                api.getMethod(),
                api.getPathTemplate(),
                api.getToolName(),
                api.getEnabled() != null && api.getEnabled() == 1,
                normalizeApiPublishStatus(api.getPublishStatus()),
                api.getPublishedVersion() == null ? 0 : api.getPublishedVersion(),
                api.getPublishedAt(),
                integrationConnectorPermissionService.canOperateConnector(connector, operator),
                api.getCreatedAt(),
                api.getUpdatedAt());
    }

    private ConnectorApiDetail toConnectorApiDetail(
            IntegrationConnector connector, IntegrationConnectorApi api, SysUserModel operator) {
        return new ConnectorApiDetail(
                api.getId(),
                connector.getId(),
                api.getConnectId(),
                api.getConnectName(),
                api.getApiCode(),
                api.getApiName(),
                api.getDescription(),
                api.getMethod(),
                api.getPathTemplate(),
                parseJsonList(api.getHeadersJson()),
                parseJsonList(api.getQueryParamsJson()),
                api.getBodyTemplateJson(),
                api.getContentType(),
                parseJsonList(api.getInputSchemaJson()),
                parseJsonList(api.getOutputMappingJson()),
                parseJsonList(api.getIdentityBindingPolicyJson()),
                "",
                api.getToolName(),
                api.getEnabled() != null && api.getEnabled() == 1,
                normalizeApiPublishStatus(api.getPublishStatus()),
                api.getPublishedVersion() == null ? 0 : api.getPublishedVersion(),
                api.getPublishedAt(),
                integrationConnectorPermissionService.canOperateConnector(connector, operator),
                api.getCreatedAt(),
                api.getUpdatedAt());
    }

    private NormalizedConnector normalizeConnectorRequest(
            CreateOrUpdateConnectorRequest request, IntegrationConnector existing) throws TaskException {
        if (request == null) {
            throw new TaskException("请求参数不能为空", TaskException.Code.UNKNOWN);
        }
        List<Map<String, Object>> authList = normalizeAuthList(
                request.authList() != null ? request.authList() : request.authConfig(),
                existing == null ? null : existing.getAuthConfigJson());
        return new NormalizedConnector(
                requireText(request.name(), "连接器名称不能为空"),
                requireText(request.alias(), "连接器编码不能为空"),
                normalizeBaseUrl(request.baseUrl()),
                authList.isEmpty() ? "NONE" : "MULTIPLE",
                authList.isEmpty() ? "" : serializeJson(Map.of("items", authList)),
                integrationConnectorPermissionService.normalizePermissionScope(
                        request.permissionScope() != null
                                ? request.permissionScope()
                                : (existing == null ? null : existing.getPermissionScope())),
                normalizeStatus(request.status(), existing == null ? null : existing.getStatus()));
    }

    private NormalizedConnectorApi normalizeApiRequest(
            CreateOrUpdateConnectorApiRequest request, IntegrationConnector connector, IntegrationConnectorApi existing)
            throws TaskException {
        if (request == null) {
            throw new TaskException("请求参数不能为空", TaskException.Code.UNKNOWN);
        }
        String apiName = requireText(request.apiName(), "API 名称不能为空");
        String apiCode = StringUtils.hasText(request.apiCode())
                ? request.apiCode().trim()
                : generateApiCode(connector, apiName);
        String url = requireText(firstText(request.url(), request.pathTemplate()), "接口地址不能为空");
        List<Map<String, Object>> authList = parseAuthItems(connector.getAuthConfigJson());
        String connectId = trimText(firstText(request.connectId(), existing == null ? "" : existing.getConnectId()));
        String connectName = trimText(firstText(request.connectName(), existing == null ? "" : existing.getConnectName()));
        if (StringUtils.hasText(connectId)) {
            Map<String, Object> authItem = findAuthItem(authList, connectId);
            if (authItem == null) {
                throw new TaskException("所选鉴权不存在或已被删除", TaskException.Code.UNKNOWN);
            }
            connectName = firstText(authItem.get("name"));
        } else {
            connectName = "";
        }

        List<Map<String, Object>> headers = normalizeNamedValueList(request.headers());
        List<Map<String, Object>> forms = normalizeNamedValueList(firstNonNull(request.forms(), request.queryParams()));
        List<Map<String, Object>> inputParams =
                normalizeParamList(firstNonNull(request.inputParams(), request.inputSchema()));
        List<Map<String, Object>> outputParams =
                normalizeParamList(firstNonNull(request.outputParams(), request.outputMapping()));
        List<Map<String, Object>> returnInfo = normalizeParamList(request.returnInfo());
        String body = normalizeBody(firstNonNull(request.body(), request.bodyTemplate()));
        boolean enabled = request.enabled() == null ? existing == null || existing.getEnabled() == null || existing.getEnabled() == 1 : request.enabled();
        String toolName = StringUtils.hasText(request.toolName())
                ? request.toolName().trim()
                : (existing != null && StringUtils.hasText(existing.getToolName())
                        ? existing.getToolName().trim()
                        : buildToolName(connector.getId(), apiCode));

        return new NormalizedConnectorApi(
                apiCode,
                apiName,
                trimText(firstText(request.apiRemark(), request.description())),
                connectId,
                connectName,
                normalizeMethod(request.method()),
                url,
                serializeJson(headers),
                serializeJson(forms),
                body,
                normalizeContentType(request.contentType()),
                serializeJson(inputParams),
                serializeJson(outputParams),
                serializeJson(returnInfo),
                trimText(request.returnJsonStr()),
                normalizeToolName(toolName),
                enabled ? 1 : 0);
    }

    private void applyNormalizedConnector(IntegrationConnector connector, NormalizedConnector normalized) {
        connector.setName(normalized.name());
        connector.setAlias(normalized.alias());
        connector.setBaseUrl(normalized.baseUrl());
        connector.setAuthType(normalized.authType());
        connector.setAuthConfigJson(normalized.authConfigJson());
        connector.setConnectParamsJson("");
        connector.setPermissionScope(normalized.permissionScope());
        connector.setStatus(normalized.status());
    }

    private void applyNormalizedApi(IntegrationConnectorApi api, NormalizedConnectorApi normalized) {
        api.setApiCode(normalized.apiCode());
        api.setApiName(normalized.apiName());
        api.setDescription(normalized.apiRemark());
        api.setConnectId(normalized.connectId());
        api.setConnectName(normalized.connectName());
        api.setMethod(normalized.method());
        api.setPathTemplate(normalized.url());
        api.setHeadersJson(normalized.headersJson());
        api.setQueryParamsJson(normalized.formsJson());
        api.setBodyTemplateJson(normalized.body());
        api.setContentType(normalized.contentType());
        api.setInputSchemaJson(normalized.inputParamsJson());
        api.setOutputMappingJson(normalized.outputParamsJson());
        api.setIdentityBindingPolicyJson(normalized.returnInfoJson());
        api.setToolName(normalized.toolName());
        api.setEnabled(normalized.enabled());
    }

    private Map<String, Object> resolveAuthItemForTest(IntegrationConnector connector, ConnectorAuthTestRequest request)
            throws TaskException {
        if (request != null && request.authItem() != null) {
            return normalizeAuthItem(normalizeObjectMap(request.authItem()), 0);
        }
        List<Map<String, Object>> authList = parseAuthItems(connector.getAuthConfigJson());
        String authId = request == null ? "" : trimText(request.authId());
        Map<String, Object> authItem = StringUtils.hasText(authId)
                ? findAuthItem(authList, authId)
                : (authList.isEmpty() ? null : authList.get(0));
        if (authItem == null) {
            throw new TaskException("当前连接器未配置可用鉴权", TaskException.Code.UNKNOWN);
        }
        return authItem;
    }

    private List<Map<String, Object>> normalizeAuthList(Object raw, String existingJson) throws TaskException {
        List<Map<String, Object>> source = extractAuthItems(raw);
        if (source.isEmpty() && StringUtils.hasText(existingJson)) {
            source = parseAuthItems(existingJson);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            Map<String, Object> normalized = normalizeAuthItem(source.get(index), index);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private Map<String, Object> normalizeAuthItem(Map<String, Object> rawItem, int index) throws TaskException {
        if (rawItem.isEmpty()) {
            return null;
        }
        Map<String, Object> source = normalizeObjectMap(firstNonNull(rawItem.get("config"), rawItem));
        Map<String, Object> authInfoSource = normalizeObjectMap(firstNonNull(rawItem.get("authInfo"), source));
        String authType = firstText(rawItem.get("authType"), rawItem.get("type"), "OAUTH2_CLIENT_CREDENTIALS")
                .toUpperCase(Locale.ROOT);
        if (!"OAUTH2_CLIENT_CREDENTIALS".equals(authType)) {
            throw new TaskException("当前仅支持 OAuth 2.0 鉴权", TaskException.Code.UNKNOWN);
        }
        String id = StringUtils.hasText(firstText(rawItem.get("id"), rawItem.get("objectId")))
                ? firstText(rawItem.get("id"), rawItem.get("objectId"))
                : generateObjectId();
        String name = requireText(firstText(rawItem.get("name"), "鉴权" + (index + 1)), "鉴权名称不能为空");
        Map<String, Object> request = normalizeObjectMap(firstNonNull(authInfoSource.get("request"), authInfoSource));
        Map<String, Object> response = normalizeObjectMap(firstNonNull(authInfoSource.get("response"), authInfoSource));

        Map<String, Object> authInfo = new LinkedHashMap<>();
        authInfo.put("method", normalizeMethod(firstText(request.get("method"), authInfoSource.get("requestMethod"), "POST")));
        authInfo.put("url", trimText(firstText(request.get("url"), authInfoSource.get("url"), authInfoSource.get("tokenUrl"))));
        authInfo.put("headers", normalizeNamedValueList(firstNonNull(request.get("headers"), authInfoSource.get("headers"))));
        authInfo.put("forms", normalizeNamedValueList(firstNonNull(
                request.get("forms"), request.get("formParams"), authInfoSource.get("forms"), authInfoSource.get("formParams"))));
        authInfo.put("body", normalizeBody(firstNonNull(request.get("body"), authInfoSource.get("body"))));
        authInfo.put("expireAfterMinutes", normalizePositiveInt(
                firstNonNull(response.get("expireAfterMinutes"), authInfoSource.get("expireAfterMinutes")),
                120));
        authInfo.put("returnInfo", normalizeParamList(firstNonNull(response.get("returnInfo"), authInfoSource.get("returnInfo"))));
        authInfo.put("returnJsonStr", trimText(firstText(response.get("returnJsonStr"), authInfoSource.get("returnJsonStr"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", name);
        result.put("remark", trimText(firstText(rawItem.get("remark"))));
        result.put("authType", authType);
        result.put("authInfo", authInfo);
        result.put("state", normalizeState(rawItem.get("state")));
        return result;
    }

    private List<Map<String, Object>> extractAuthItems(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(this::normalizeObjectMap).filter(item -> !item.isEmpty()).toList();
        }
        Map<String, Object> map = normalizeObjectMap(raw);
        Object items = firstNonNull(map.get("items"), map.get("authList"), map.get("authentications"));
        if (items instanceof List<?> list) {
            return list.stream().map(this::normalizeObjectMap).filter(item -> !item.isEmpty()).toList();
        }
        if (map.isEmpty()) {
            return List.of();
        }
        return List.of(map);
    }

    private List<Map<String, Object>> parseAuthItems(String json) {
        Map<String, Object> map = parseJsonMap(json);
        Object items = firstNonNull(map.get("items"), map.get("authList"), map.get("authentications"));
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::normalizeObjectMap).filter(item -> !item.isEmpty()).toList();
    }

    private Map<String, Object> findAuthItem(List<Map<String, Object>> authList, String authId) {
        if (!StringUtils.hasText(authId)) {
            return null;
        }
        return authList.stream()
                .filter(item -> authId.trim().equals(firstText(item.get("id"), item.get("objectId"))))
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> normalizeNamedValueList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = normalizeObjectMap(item);
            if (!StringUtils.hasText(firstText(map.get("name")))) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", firstText(map.get("name")));
            row.put("value", text(map.get("value")));
            row.put("description", firstText(map.get("description"), map.get("desc")));
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> normalizeParamList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> normalized = normalizeParamItem(normalizeObjectMap(item));
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private Map<String, Object> normalizeParamItem(Map<String, Object> source) {
        if (!StringUtils.hasText(firstText(source.get("name")))) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objectId", firstText(source.get("objectId"), source.get("id"), generateObjectId()));
        result.put("parentId", firstText(source.get("parentId")));
        result.put("name", firstText(source.get("name")));
        result.put("key", firstText(source.get("key")));
        result.put("path", firstText(source.get("path")));
        result.put("paramType", normalizeParamType(firstText(source.get("paramType"), "string")));
        result.put("value", source.get("value"));
        result.put("desc", firstText(source.get("desc"), source.get("description")));
        List<Map<String, Object>> children = normalizeParamList(source.get("children"));
        if (!children.isEmpty()) {
            result.put("children", children);
        }
        return result;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }

    private String normalizeOptionalStatus(String status) {
        String normalized = trimText(status).toUpperCase(Locale.ROOT);
        return List.of("ACTIVE", "DRAFT", "DISABLED").contains(normalized) ? normalized : "";
    }

    private String normalizeStatus(String status, String existingStatus) {
        String normalized = trimText(StringUtils.hasText(status) ? status : existingStatus).toUpperCase(Locale.ROOT);
        return List.of("ACTIVE", "DRAFT", "DISABLED").contains(normalized) ? normalized : "DRAFT";
    }

    private String normalizeMethod(String method) {
        String normalized = trimText(method).toUpperCase(Locale.ROOT);
        return List.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(normalized) ? normalized : "GET";
    }

    private String normalizeContentType(String contentType) {
        String normalized = trimText(contentType);
        return normalized;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = trimText(baseUrl);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.replaceAll("/+$", "");
    }

    private String normalizeBody(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof String text) {
            return text.trim();
        }
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception ex) {
            return String.valueOf(raw);
        }
    }

    private String normalizeParamType(String paramType) {
        String normalized = trimText(paramType).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "number", "integer", "long", "double" -> "number";
            case "object" -> "object";
            case "objectarray", "array", "list" -> "array";
            case "boolean", "bool" -> "boolean";
            default -> "string";
        };
    }

    private String normalizeJsonPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String normalized = path.trim();
        if (normalized.startsWith("$")) {
            return normalized;
        }
        if (normalized.startsWith(".")) {
            return "$" + normalized;
        }
        return "$." + normalized;
    }

    private Integer normalizeState(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String value = text(raw);
        if ("0".equals(value)) {
            return 0;
        }
        return 1;
    }

    private int normalizePositiveInt(Object raw, int defaultValue) {
        if (raw instanceof Number number) {
            return number.intValue() > 0 ? number.intValue() : defaultValue;
        }
        String value = text(raw);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private Map<String, Object> normalizeObjectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private Map<String, Object> normalizeInputArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(arguments);
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

    private String normalizeCompareText(String value) {
        return trimText(value).toLowerCase(Locale.ROOT);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String generateObjectId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String generateApiCode(IntegrationConnector connector, String apiName) {
        String connectorCode = normalizeConnectorCode(connector == null ? "" : connector.getAlias());
        String apiNameCode = generateCodeInitials(apiName, "api");
        if (!StringUtils.hasText(connectorCode)) {
            return apiNameCode;
        }
        return connectorCode + "-" + apiNameCode;
    }

    private String normalizeConnectorCode(String alias) {
        String normalized = trimText(alias)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^-+|-+$", "")
                .replaceAll("^_+|_+$", "");
        return StringUtils.hasText(normalized) ? normalized : "";
    }

    private String generateCodeInitials(String source, String fallback) {
        String text = trimText(source);
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        StringBuilder builder = new StringBuilder();
        boolean lastUnderscore = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isWhitespace(current) || (!Character.isLetterOrDigit(current) && current < 128)) {
                if (builder.length() > 0 && !lastUnderscore) {
                    builder.append('_');
                    lastUnderscore = true;
                }
                continue;
            }
            if (current < 128 && Character.isLetterOrDigit(current)) {
                builder.append(Character.toLowerCase(current));
                lastUnderscore = false;
                continue;
            }
            String initial = toPinyinInitial(String.valueOf(current));
            if (StringUtils.hasText(initial)) {
                builder.append(initial);
                lastUnderscore = false;
                continue;
            }
            if (builder.length() > 0 && !lastUnderscore) {
                builder.append('_');
                lastUnderscore = true;
            }
        }
        String base = builder.toString().replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return StringUtils.hasText(base) ? base : fallback;
    }

    private String toPinyinInitial(String text) {
        try {
            String pinyin = PinyinHelper.toPinyin(text, PinyinStyleEnum.NORMAL);
            if (!StringUtils.hasText(pinyin)) {
                return "";
            }
            String normalized = pinyin.replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(normalized)) {
                return "";
            }
            return normalized.substring(0, 1);
        } catch (Exception exception) {
            return "";
        }
    }

    private String buildToolName(Long connectorId, String apiCode) {
        return "connector_" + connectorId + "_" + normalizeToolName(apiCode);
    }

    private boolean isApiPublished(IntegrationConnectorApi api) {
        return api != null
                && API_PUBLISH_STATUS_PUBLISHED.equalsIgnoreCase(normalizeApiPublishStatus(api.getPublishStatus()));
    }

    private String normalizeApiPublishStatus(String publishStatus) {
        String normalized = trimText(publishStatus).toUpperCase(Locale.ROOT);
        return API_PUBLISH_STATUS_PUBLISHED.equals(normalized) ? API_PUBLISH_STATUS_PUBLISHED : API_PUBLISH_STATUS_DRAFT;
    }

    private String normalizeToolName(String toolName) {
        String sanitized = trimText(toolName).replaceAll("[^A-Za-z0-9_]+", "_");
        sanitized = sanitized.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return StringUtils.hasText(sanitized) ? sanitized : "connector_api_" + System.currentTimeMillis();
    }

    private record NormalizedConnector(
            String name,
            String alias,
            String baseUrl,
            String authType,
            String authConfigJson,
            Integer permissionScope,
            String status) {}

    private record NormalizedConnectorApi(
            String apiCode,
            String apiName,
            String apiRemark,
            String connectId,
            String connectName,
            String method,
            String url,
            String headersJson,
            String formsJson,
            String body,
            String contentType,
            String inputParamsJson,
            String outputParamsJson,
            String returnInfoJson,
            String returnJsonStr,
            String toolName,
            Integer enabled) {}

    public record CreateOrUpdateConnectorRequest(
            String name,
            String alias,
            String baseUrl,
            String authType,
            Object authConfig,
            Object authList,
            Integer permissionScope,
            String status) {}

    public record ConnectorAuthTestRequest(String authId, Object authItem, Map<String, Object> variables) {}

    public record CreateOrUpdateConnectorApiRequest(
            String apiCode,
            String apiName,
            String description,
            String apiRemark,
            String connectId,
            String connectName,
            String method,
            String pathTemplate,
            String url,
            Object headers,
            Object queryParams,
            Object forms,
            Object bodyTemplate,
            Object body,
            String contentType,
            Object inputSchema,
            Object inputParams,
            Object outputMapping,
            Object outputParams,
            Object returnInfo,
            String returnJsonStr,
            String toolName,
            Boolean enabled) {}

    public record ConnectorApiTestRequest(Map<String, Object> arguments) {}

    public record ConnectorSummary(
            Long id,
            String name,
            String alias,
            String baseUrl,
            String authType,
            Integer authCount,
            Long ownerUserId,
            Integer permissionScope,
            boolean canOperate,
            boolean canChangePermission,
            String status,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record ConnectorPageResult(List<ConnectorSummary> list, long total, int page, int pageSize) {}

    public record ConnectorDetail(
            Long id,
            String name,
            String alias,
            String baseUrl,
            String authType,
            List<ConnectorAuthDetail> authList,
            Long ownerUserId,
            Integer permissionScope,
            boolean canOperate,
            boolean canChangePermission,
            String status,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record ConnectorAuthDetail(
            String id, String name, String remark, String authType, Object authInfo, Integer state) {}

    public record ConnectorApiSummary(
            Long id,
            Long connectorId,
            String connectId,
            String connectName,
            String apiCode,
            String apiName,
            String apiRemark,
            String method,
            String url,
            String toolName,
            boolean enabled,
            String publishStatus,
            int publishedVersion,
            java.util.Date publishedAt,
            boolean canOperate,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record ConnectorApiCodePreview(String apiCode) {}

    public record ConnectorCodePreview(String connectorCode) {}

    public record ConnectorApiDetail(
            Long id,
            Long connectorId,
            String connectId,
            String connectName,
            String apiCode,
            String apiName,
            String apiRemark,
            String method,
            String url,
            Object headers,
            Object forms,
            String body,
            String contentType,
            Object inputParams,
            Object outputParams,
            Object returnInfo,
            String returnJsonStr,
            String toolName,
            boolean enabled,
            String publishStatus,
            int publishedVersion,
            java.util.Date publishedAt,
            boolean canOperate,
            java.util.Date createdAt,
            java.util.Date updatedAt) {}

    public record ConnectorAuthTestResult(
            String method,
            String requestUrl,
            Map<String, Object> requestHeaders,
            Map<String, Object> requestQuery,
            Object requestBody,
            Object rawResponse,
            String jsonStr,
            List<Map<String, Object>> returnInfo,
            String accessToken,
            Long expiresInSeconds,
            String tokenType,
            String message) {}

    public record ConnectorApiTestResult(
            String method,
            String requestUrl,
            Map<String, Object> requestHeaders,
            Map<String, Object> requestQuery,
            Object requestBody,
            Object rawResponse,
            String jsonStr,
            List<Map<String, Object>> returnInfo,
            Object result) {}
}
