package lingzhou.agent.backend.capability.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.common.security.Rsa;
import lingzhou.agent.backend.business.system.model.PlatformAuthConfig;
import lingzhou.agent.backend.business.system.model.PlatformEndpointItem;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class LowcodePlatformClient {

    private static final Logger logger = LoggerFactory.getLogger(LowcodePlatformClient.class);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(20);
    private static final String TOKEN_PATH = "/api/Permission/GetToken";
    private static final String TOKEN_CODE = "administrator";
    private static final String TOKEN_PASSWORD = "#zhouju@2023!.$&";
    private static final String TOKEN_TENANT_CODE = "00000000";
    private static final String APP_LIST_PATH = "/api/IntegrationConnect/GetAppList";
    private static final String ACCESS_APP_LIST_PATH = "/api/App/GetListMyAccessApps";
    private static final String API_LIST_PATH_TEMPLATE = "/api/IntegrationConnect/LoadApiList/{appId}";
    private static final String APP_MENU_PATH_TEMPLATE = "/api/App/GetAppActiveMenuByCode/{appCode}";
    private static final String DATA_SOURCE_NEW_PATH_TEMPLATE = "/api/DataSource/GetDataSourceNew/{formCode}/true";
    private static final String SQL_SELECT_PATH = "/api/FrontEnd/sqlSelect";
    private static final String EXECUTE_API_PATH_TEMPLATE = "/api/IntegrationConnect/ExecuteApi/{apiCode}";
    private static final int APP_LIST_PAGE_NO = 1;
    private static final int APP_LIST_PAGE_SIZE = 10000;

    private final ObjectMapper objectMapper;

    public LowcodePlatformClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PlatformEnvelope getToken(PlatformEndpointItem platform) throws TaskException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("Code", TOKEN_CODE);
        requestBody.put("Password", encryptFixedPassword());
        requestBody.put("TenantCode", TOKEN_TENANT_CODE);

        try {
            Object response = buildRestClient(platform)
                    .post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Object.class);
            return parseEnvelope(response);
        } catch (RestClientResponseException ex) {
            String body = shorten(ex.getResponseBodyAsString(), 300);
            logger.warn(
                    "低代码平台获取 token 请求失败：platformKey={}, status={}, body={}",
                    platform.getKey(),
                    ex.getStatusCode().value(),
                    body);
            throw new TaskException(
                    "平台登录失败，HTTP " + ex.getStatusCode().value() + "：" + body,
                    TaskException.Code.UNKNOWN,
                    ex);
        } catch (TaskException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.warn("低代码平台获取 token 失败：platformKey={}, error={}", platform.getKey(), ex.getMessage(), ex);
            throw new TaskException("平台登录失败", TaskException.Code.UNKNOWN, ex);
        }
    }

    public List<Map<String, Object>> getAppList(PlatformEndpointItem platform, String token) throws TaskException {
        PlatformAuthConfig authConfig = getAuthConfig(platform);
        try {
            RestClient.RequestBodySpec request =
                    buildRestClient(platform).post().uri(APP_LIST_PATH).contentType(MediaType.APPLICATION_JSON);
            applyCommonHeaders(request, token, authConfig);
            Object response = request.body(buildAppListRequestBody()).retrieve().body(Object.class);
            PlatformEnvelope envelope = parseEnvelope(response);
            return extractObjectList(envelope.result(), "Datas");
        } catch (RestClientResponseException ex) {
            throw toHttpException("获取连接器应用列表失败", platform, ex);
        }
    }

    public List<Map<String, Object>> getAccessibleApps(PlatformEndpointItem platform, String token) throws TaskException {
        PlatformAuthConfig authConfig = getAuthConfig(platform);
        try {
            RestClient.RequestHeadersSpec<?> request = buildRestClient(platform).get().uri(ACCESS_APP_LIST_PATH);
            applyCommonHeaders(request, token, authConfig);
            Object response = request.retrieve().body(Object.class);
            PlatformEnvelope envelope = parseEnvelope(response);
            return extractObjectList(envelope.result(), null);
        } catch (RestClientResponseException ex) {
            throw toHttpException("获取可访问应用列表失败", platform, ex);
        }
    }

    public List<Map<String, Object>> loadApiList(PlatformEndpointItem platform, String token, String appId) throws TaskException {
        PlatformAuthConfig authConfig = getAuthConfig(platform);
        if (!StringUtils.hasText(appId)) {
            throw new TaskException("appId 不能为空", TaskException.Code.UNKNOWN);
        }
        try {
            RestClient.RequestHeadersSpec<?> request = buildRestClient(platform).get().uri(API_LIST_PATH_TEMPLATE, appId.trim());
            applyCommonHeaders(request, token, authConfig);
            Object response = request.retrieve().body(Object.class);
            PlatformEnvelope envelope = parseEnvelope(response);
            return extractObjectList(envelope.result(), null);
        } catch (RestClientResponseException ex) {
            throw toHttpException("获取 API 列表失败", platform, ex);
        }
    }

    public List<Map<String, Object>> getAppActiveMenus(PlatformEndpointItem platform, String token, String appCode)
            throws TaskException {
        PlatformAuthConfig authConfig = getAuthConfig(platform);
        if (!StringUtils.hasText(appCode)) {
            throw new TaskException("appCode 不能为空", TaskException.Code.UNKNOWN);
        }
        try {
            RestClient.RequestHeadersSpec<?> request =
                    buildRestClient(platform).get().uri(APP_MENU_PATH_TEMPLATE, appCode.trim());
            applyCommonHeaders(request, token, authConfig);
            Object response = request.retrieve().body(Object.class);
            PlatformEnvelope envelope = parseEnvelope(response);
            return extractObjectList(envelope.result(), null);
        } catch (RestClientResponseException ex) {
            throw toHttpException("获取应用菜单失败", platform, ex);
        }
    }

    public Map<String, Object> getDataSourceNew(PlatformEndpointItem platform, String token, String formCode)
            throws TaskException {
        PlatformAuthConfig authConfig = getAuthConfig(platform);
        if (!StringUtils.hasText(formCode)) {
            throw new TaskException("formCode 不能为空", TaskException.Code.UNKNOWN);
        }
        try {
            RestClient.RequestHeadersSpec<?> request =
                    buildRestClient(platform).get().uri(DATA_SOURCE_NEW_PATH_TEMPLATE, formCode.trim());
            applyCommonHeaders(request, token, authConfig);
            Object response = request.retrieve().body(Object.class);
            PlatformEnvelope envelope = parseEnvelope(response);
            return extractObjectMap(envelope.result());
        } catch (RestClientResponseException ex) {
            throw toHttpException("获取菜单字段结构失败", platform, ex);
        }
    }

    public PlatformEnvelope executeApi(
            PlatformEndpointItem platform, String token, String apiCode, Map<String, Object> requestBody) throws TaskException {
        PlatformAuthConfig authConfig = getAuthConfig(platform);
        if (!StringUtils.hasText(apiCode)) {
            throw new TaskException("apiCode 不能为空", TaskException.Code.UNKNOWN);
        }
        try {
            RestClient.RequestBodySpec request = buildRestClient(platform)
                    .post()
                    .uri(EXECUTE_API_PATH_TEMPLATE, apiCode.trim())
                    .contentType(MediaType.APPLICATION_JSON);
            applyCommonHeaders(request, token, authConfig);
            if (StringUtils.hasText(authConfig.getUserId())) {
                request.header("UserId", authConfig.getUserId().trim());
            }
            Object response = request.body(requestBody == null ? Map.of() : requestBody).retrieve().body(Object.class);
            return parseEnvelope(response);
        } catch (RestClientResponseException ex) {
            throw toHttpException("执行低代码 API 失败", platform, ex);
        }
    }

    public List<Map<String, Object>> sqlSelect(
            PlatformEndpointItem platform, String token, String encryptedSql, List<Object> argsPart) throws TaskException {
        PlatformAuthConfig authConfig = getAuthConfig(platform);
        if (!StringUtils.hasText(encryptedSql)) {
            throw new TaskException("sqlPart 不能为空", TaskException.Code.UNKNOWN);
        }
        try {
            RestClient.RequestBodySpec request = buildRestClient(platform)
                    .post()
                    .uri(SQL_SELECT_PATH)
                    .contentType(MediaType.APPLICATION_JSON);
            applyCommonHeaders(request, token, authConfig);
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("sqlPart", encryptedSql.trim());
            requestBody.put("argsPart", argsPart == null ? List.of() : argsPart);
            Object response = request.body(requestBody).retrieve().body(Object.class);
            PlatformEnvelope envelope = parseEnvelope(response);
            return extractObjectList(envelope.result(), null);
        } catch (RestClientResponseException ex) {
            throw toHttpException("执行低代码 SQL 查询失败", platform, ex);
        }
    }

    private RestClient buildRestClient(PlatformEndpointItem platform) throws TaskException {
        String baseUrl = normalizeBaseUrl(platform == null ? null : platform.getApiUrl());
        if (!StringUtils.hasText(baseUrl)) {
            throw new TaskException("平台 API URL 未配置", TaskException.Code.UNKNOWN);
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(DEFAULT_READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
    }

    private PlatformAuthConfig getAuthConfig(PlatformEndpointItem platform) throws TaskException {
        if (platform == null) {
            throw new TaskException("平台配置不存在", TaskException.Code.UNKNOWN);
        }
        return platform.getAuthConfig() == null ? new PlatformAuthConfig() : platform.getAuthConfig();
    }

    private PlatformEnvelope parseEnvelope(Object rawResponse) throws TaskException {
        if (rawResponse == null) {
            throw new TaskException("低代码平台返回为空", TaskException.Code.UNKNOWN);
        }
        JsonNode root = objectMapper.valueToTree(rawResponse);
        int status = root.path("Status").asInt(Integer.MIN_VALUE);
        int code = root.path("Code").asInt(Integer.MIN_VALUE);
        String message = textOrNull(root.path("Message"));
        JsonNode resultNode = root.path("Result");
        Object result = resultNode.isMissingNode() || resultNode.isNull()
                ? null
                : objectMapper.convertValue(resultNode, Object.class);
        if (status != 1 || code != 200) {
            String resolvedMessage = StringUtils.hasText(message) ? message : "低代码平台返回失败";
            throw new TaskException(resolvedMessage, TaskException.Code.UNKNOWN);
        }
        return new PlatformEnvelope(status, code, message, result);
    }

    private List<Map<String, Object>> extractObjectList(Object rawResult, String nestedListField) {
        if (rawResult == null) {
            return List.of();
        }
        Object listSource = rawResult;
        if (StringUtils.hasText(nestedListField) && rawResult instanceof Map<?, ?> map) {
            listSource = map.get(nestedListField);
        }
        if (!(listSource instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>(rawList.size());
        for (Object rawItem : rawList) {
            if (rawItem == null) {
                continue;
            }
            Map<String, Object> item = objectMapper.convertValue(rawItem, objectMapper.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, Object.class));
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> extractObjectMap(Object rawResult) {
        if (!(rawResult instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        return objectMapper.convertValue(
                rawMap, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        return baseUrl.trim().replaceAll("/+$", "");
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, Object> buildAppListRequestBody() {
        Map<String, Object> pagingInfo = new LinkedHashMap<>();
        pagingInfo.put("PagingInfo", APP_LIST_PAGE_NO);
        pagingInfo.put("PageSize", APP_LIST_PAGE_SIZE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("PagingInfo", pagingInfo);
        return body;
    }

    private void applyCommonHeaders(
            RestClient.RequestHeadersSpec<?> request, String token, PlatformAuthConfig authConfig) {
        if (request == null) {
            return;
        }
        if (StringUtils.hasText(token)) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
        }
        String tnCode = StringUtils.hasText(authConfig.getTncode()) ? authConfig.getTncode().trim() : "00000000";
        String userId = StringUtils.hasText(authConfig.getUserId())
                ? authConfig.getUserId().trim()
                : "18f923a7-5a5e-426d-94ae-a55ad1a4b239";
        request.header("TnCode", tnCode);
        request.header("UserId", userId);
    }

    private TaskException toHttpException(String action, PlatformEndpointItem platform, RestClientResponseException ex) {
        String body = shorten(ex.getResponseBodyAsString(), 300);
        logger.warn(
                "{}：platformKey={}, status={}, body={}",
                action,
                platform.getKey(),
                ex.getStatusCode().value(),
                body);
        return new TaskException(
                action + "，HTTP " + ex.getStatusCode().value() + "：" + body,
                TaskException.Code.UNKNOWN,
                ex);
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String shorten(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String encryptFixedPassword() throws TaskException {
        try {
            return Rsa.RsaPasswordNew(TOKEN_PASSWORD);
        } catch (Exception ex) {
            throw new TaskException("固定密码加密失败", TaskException.Code.UNKNOWN, ex);
        }
    }

    public record PlatformEnvelope(int status, int code, String message, Object result) {}
}
