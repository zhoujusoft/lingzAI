package lingzhou.agent.backend.capability.api.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lingzhou.agent.backend.business.integration.domain.IntegrationConnector;
import lingzhou.agent.backend.business.integration.domain.IntegrationConnectorApi;
import lingzhou.agent.backend.business.integration.service.connector.IntegrationConnectorPermissionService;
import lingzhou.agent.backend.business.system.model.SysUserModel;
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
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ConnectorApiExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorApiExecutor.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {};
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$([A-Za-z0-9_\\.\\[\\]]+)\\$");
    private static final Pattern LEGACY_TOKEN_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern SINGLE_TOKEN_PATTERN = Pattern.compile("^\\$([A-Za-z0-9_\\.\\[\\]]+)\\$$");
    private static final Pattern LEGACY_SINGLE_TOKEN_PATTERN = Pattern.compile("^\\$\\{([^}]+)}$");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper objectMapper;
    private final ConnectorIdentityBindingService connectorIdentityBindingService;
    private final IntegrationConnectorPermissionService integrationConnectorPermissionService;
    private final ConcurrentMap<String, CachedAuthResult> authCache = new ConcurrentHashMap<>();

    public ConnectorApiExecutor(
            ObjectMapper objectMapper,
            ConnectorIdentityBindingService connectorIdentityBindingService,
            IntegrationConnectorPermissionService integrationConnectorPermissionService) {
        this.objectMapper = objectMapper;
        this.connectorIdentityBindingService = connectorIdentityBindingService;
        this.integrationConnectorPermissionService = integrationConnectorPermissionService;
    }

    public Object executeForTool(
            IntegrationConnector connector, IntegrationConnectorApi api, Map<String, Object> arguments) throws TaskException {
        return execute(connector, api, arguments).result();
    }

    public ExecutionDebugResult executeForTest(
            IntegrationConnector connector, IntegrationConnectorApi api, Map<String, Object> arguments) throws TaskException {
        return execute(connector, api, arguments);
    }

    public AuthExecutionDebugResult executeAuthenticationForTest(
            IntegrationConnector connector, Map<String, Object> authItem, Map<String, Object> variables) throws TaskException {
        assertCanExecute(connector);
        Map<String, Object> templateVariables =
                connectorIdentityBindingService.buildTemplateVariables(variables == null ? Map.of() : variables);
        return executeAuthentication(connector, authItem, templateVariables, true);
    }

    private ExecutionDebugResult execute(
            IntegrationConnector connector, IntegrationConnectorApi api, Map<String, Object> arguments) throws TaskException {
        assertCanExecute(connector);
        Map<String, Object> input = arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
        Map<String, Object> variables = connectorIdentityBindingService.buildTemplateVariables(input);

        if (StringUtils.hasText(api.getConnectId())) {
            Map<String, Object> authItem = findAuthItem(parseAuthItems(connector.getAuthConfigJson()), api.getConnectId());
            if (authItem == null) {
                throw new TaskException("接口绑定的鉴权不存在或已被删除", TaskException.Code.UNKNOWN);
            }
            AuthExecutionDebugResult authResult = executeAuthentication(connector, authItem, variables, false);
            variables.put("auth", authResult.rawResponse());
        }

        Map<String, Object> requestHeaders = renderNamedValues(parseNamedValueList(api.getHeadersJson()), variables);
        List<Map<String, Object>> formRows = parseNamedValueList(api.getQueryParamsJson());
        Map<String, Object> formValues = renderNamedValues(formRows, variables);
        String url = renderToString(api.getPathTemplate(), variables);
        Object requestBody = buildApiRequestBody(api, formValues, variables);

        if (StringUtils.hasText(api.getContentType())) {
            requestHeaders.putIfAbsent(HttpHeaders.CONTENT_TYPE, api.getContentType().trim());
        } else if (requestBody != null) {
            requestHeaders.putIfAbsent(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        }
        requestHeaders.putIfAbsent(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        String method = normalizeHttpMethod(api.getMethod(), "GET");
        String requestUrl = buildRequestUrl(connector.getBaseUrl(), url, Map.of());
        Object rawResponse = invokeRemoteApi(requestUrl, method, requestHeaders, requestBody);
        List<Map<String, Object>> returnInfo = collectReturnInfo(rawResponse, "output");
        String jsonStr = toJsonString(rawResponse);

        return new ExecutionDebugResult(
                method,
                requestUrl,
                maskHeaders(requestHeaders),
                Map.of(),
                requestBody,
                rawResponse,
                jsonStr,
                returnInfo,
                rawResponse);
    }

    private void assertCanExecute(IntegrationConnector connector) throws TaskException {
        Long operatorUserId = connectorIdentityBindingService.resolveCurrentUserId();
        SysUserModel operator = integrationConnectorPermissionService.resolveOperator(operatorUserId);
        integrationConnectorPermissionService.assertCanOperateConnector(connector, operator);
    }

    private AuthExecutionDebugResult executeAuthentication(
            IntegrationConnector connector,
            Map<String, Object> authItem,
            Map<String, Object> templateVariables,
            boolean skipCache)
            throws TaskException {
        Map<String, Object> authInfo = normalizeObjectMap(authItem.get("authInfo"));
        String authId = firstText(authItem.get("id"), generateObjectId());
        String cacheKey = buildAuthCacheKey(connector.getId(), authId, authInfo, templateVariables);
        if (!skipCache) {
            CachedAuthResult cached = authCache.get(cacheKey);
            if (cached != null && !cached.expired()) {
                return cached.result();
            }
        }

        String method = normalizeHttpMethod(firstText(authInfo.get("method")), "POST");
        String url = renderToString(firstText(authInfo.get("url")), templateVariables);
        Map<String, Object> headers = renderNamedValues(normalizeNamedValueList(authInfo.get("headers")), templateVariables);
        Map<String, Object> forms = renderNamedValues(normalizeNamedValueList(authInfo.get("forms")), templateVariables);
        String bodyTemplate = firstText(authInfo.get("body"));
        Object requestBody = buildAuthRequestBody(method, authInfo, forms, bodyTemplate, templateVariables, headers);

        headers.putIfAbsent(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        String requestUrl = buildRequestUrl(connector.getBaseUrl(), url, Map.of());
        Object rawResponse = invokeRemoteApi(requestUrl, method, headers, requestBody);
        List<Map<String, Object>> returnInfo = collectReturnInfo(rawResponse, "auth");
        String jsonStr = toJsonString(rawResponse);

        String accessTokenField = "$.access_token";
        String expiresInField = "$.expires_in";
        String tokenTypeField = "$.token_type";
        String accessToken = text(readJsonPath(rawResponse, accessTokenField));
        String tokenType = text(readJsonPath(rawResponse, tokenTypeField));
        long expiresInSeconds = normalizeExpiresInSeconds(
                readJsonPath(rawResponse, expiresInField), authInfo.get("expireAfterMinutes"));
        String message = StringUtils.hasText(accessToken) ? "鉴权执行成功" : "未从鉴权返回中解析到访问令牌";

        AuthExecutionDebugResult result = new AuthExecutionDebugResult(
                method,
                requestUrl,
                maskHeaders(headers),
                Map.of(),
                requestBody,
                rawResponse,
                jsonStr,
                returnInfo,
                accessToken,
                expiresInSeconds,
                tokenType,
                message);
        if (!skipCache && StringUtils.hasText(accessToken)) {
            authCache.put(cacheKey, new CachedAuthResult(result, System.currentTimeMillis() + Math.max(60L, expiresInSeconds - 30L) * 1000L));
        }
        return result;
    }

    private Object buildAuthRequestBody(
            String method,
            Map<String, Object> authInfo,
            Map<String, Object> forms,
            String bodyTemplate,
            Map<String, Object> variables,
            Map<String, Object> headers)
            throws TaskException {
        if (!List.of("POST", "PUT", "PATCH").contains(method)) {
            return null;
        }
        if (!forms.isEmpty()) {
            headers.putIfAbsent(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
            return buildFormBody(forms);
        }
        if (!StringUtils.hasText(bodyTemplate)) {
            return null;
        }
        headers.putIfAbsent(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        Object rendered = renderValue(bodyTemplate, variables);
        return coerceBody(rendered, MediaType.APPLICATION_JSON_VALUE.equals(headers.get(HttpHeaders.CONTENT_TYPE)));
    }

    private Object buildApiRequestBody(
            IntegrationConnectorApi api, Map<String, Object> forms, Map<String, Object> variables) throws TaskException {
        String method = normalizeHttpMethod(api.getMethod(), "GET");
        if (!List.of("POST", "PUT", "PATCH").contains(method)) {
            return null;
        }
        if (MediaType.APPLICATION_FORM_URLENCODED_VALUE.equalsIgnoreCase(text(api.getContentType()))) {
            return forms.isEmpty() ? null : buildFormBody(forms);
        }
        String bodyTemplate = text(api.getBodyTemplateJson());
        if (StringUtils.hasText(bodyTemplate)) {
            return coerceBody(renderValue(bodyTemplate, variables), MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(text(api.getContentType())));
        }
        if (!forms.isEmpty()) {
            return forms;
        }
        Map<String, Object> input = normalizeObjectMap(variables.get("input"));
        return input.isEmpty() ? null : input;
    }

    private Object renderValue(Object template, Map<String, Object> variables) throws TaskException {
        if (template == null) {
            return null;
        }
        if (template instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                result.put(String.valueOf(entry.getKey()), renderValue(entry.getValue(), variables));
            }
            return result;
        }
        if (template instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(renderValue(item, variables));
            }
            return result;
        }
        if (template instanceof String text) {
            return renderString(text, variables);
        }
        return template;
    }

    private String renderToString(String template, Map<String, Object> variables) throws TaskException {
        Object value = renderValue(template, variables);
        return value == null ? "" : String.valueOf(value);
    }

    private Object renderString(String template, Map<String, Object> variables) {
        if (!StringUtils.hasText(template)) {
            return template;
        }
        Matcher single = SINGLE_TOKEN_PATTERN.matcher(template);
        if (single.matches()) {
            return resolveTokenValue(single.group(1), variables);
        }
        Matcher legacySingle = LEGACY_SINGLE_TOKEN_PATTERN.matcher(template);
        if (legacySingle.matches()) {
            return resolveTokenValue(legacySingle.group(1), variables);
        }
        String rendered = replaceTokens(template, variables, TOKEN_PATTERN);
        return replaceTokens(rendered, variables, LEGACY_TOKEN_PATTERN);
    }

    private String replaceTokens(String template, Map<String, Object> variables, Pattern pattern) {
        Matcher matcher = pattern.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(stringifyScalar(resolveTokenValue(matcher.group(1), variables))));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private Object resolveTokenValue(String token, Map<String, Object> variables) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String normalized = token.replaceAll("\\[(\\d+)]", ".$1");
        Object current = variables;
        for (String part : normalized.split("\\.")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
                continue;
            }
            if (current instanceof List<?> list && part.matches("\\d+")) {
                int index = Integer.parseInt(part);
                current = index >= 0 && index < list.size() ? list.get(index) : null;
                continue;
            }
            return null;
        }
        return current;
    }

    private Map<String, Object> renderNamedValues(List<Map<String, Object>> rows, Map<String, Object> variables)
            throws TaskException {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String name = firstText(row.get("name"));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            result.put(name, renderValue(row.get("value"), variables));
        }
        return result;
    }

    private List<Map<String, Object>> normalizeNamedValueList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(this::normalizeObjectMap).toList();
        }
        return List.of();
    }

    private List<Map<String, Object>> parseNamedValueList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseAuthItems(String json) {
        Map<String, Object> root = parseJsonMap(json);
        Object items = root.get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::normalizeObjectMap).toList();
    }

    private Map<String, Object> findAuthItem(List<Map<String, Object>> authItems, String authId) {
        if (!StringUtils.hasText(authId)) {
            return null;
        }
        return authItems.stream()
                .filter(item -> authId.trim().equals(firstText(item.get("id"), item.get("objectId"))))
                .findFirst()
                .orElse(null);
    }

    private String buildAuthCacheKey(
            Long connectorId, String authId, Map<String, Object> authInfo, Map<String, Object> templateVariables) {
        return connectorId
                + "|"
                + authId
                + "|"
                + Integer.toHexString(String.valueOf(authInfo).hashCode())
                + "|"
                + Integer.toHexString(String.valueOf(templateVariables).hashCode());
    }

    private long normalizeExpiresInSeconds(Object rawExpiresIn, Object expireAfterMinutes) {
        if (rawExpiresIn instanceof Number number) {
            return Math.max(number.longValue(), 60L);
        }
        String expiresText = text(rawExpiresIn);
        if (expiresText.matches("\\d+")) {
            return Math.max(Long.parseLong(expiresText), 60L);
        }
        String fallback = text(expireAfterMinutes);
        if (fallback.matches("\\d+")) {
            return Math.max(Long.parseLong(fallback) * 60L, 60L);
        }
        return 7200L;
    }

    private Object readJsonPath(Object raw, String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        if ("$".equals(path.trim())) {
            return raw;
        }
        String normalized = path.trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replaceAll("\\[(\\d+)]", ".$1");
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        Object current = raw;
        for (String part : normalized.split("\\.")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
                continue;
            }
            if (current instanceof List<?> list && part.matches("\\d+")) {
                int index = Integer.parseInt(part);
                current = index >= 0 && index < list.size() ? list.get(index) : null;
                continue;
            }
            return null;
        }
        return current;
    }

    private List<Map<String, Object>> collectReturnInfo(Object value, String rootKey) {
        List<Map<String, Object>> result = new ArrayList<>();
        collectReturnInfo(value, rootKey, "$", result);
        return result;
    }

    private void collectReturnInfo(Object value, String keyPath, String jsonPath, List<Map<String, Object>> target) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String key = String.valueOf(entry.getKey());
                String nextKeyPath = StringUtils.hasText(keyPath) ? keyPath + "." + key : key;
                String nextJsonPath = "$".equals(jsonPath) ? "$." + key : jsonPath + "." + key;
                appendReturnInfoRow(target, key, nextKeyPath, nextJsonPath, entry.getValue());
                collectReturnInfo(entry.getValue(), nextKeyPath, nextJsonPath, target);
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (int index = 0; index < list.size() && index < 20; index++) {
                String key = String.valueOf(index);
                String nextKeyPath = StringUtils.hasText(keyPath) ? keyPath + "." + key : key;
                String nextJsonPath = "$".equals(jsonPath) ? "$[" + index + "]" : jsonPath + "[" + index + "]";
                appendReturnInfoRow(target, key, nextKeyPath, nextJsonPath, list.get(index));
                collectReturnInfo(list.get(index), nextKeyPath, nextJsonPath, target);
            }
        }
    }

    private void appendReturnInfoRow(
            List<Map<String, Object>> target, String name, String key, String path, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("objectId", generateObjectId());
        row.put("name", name);
        row.put("key", key);
        row.put("path", path);
        row.put("paramType", detectParamType(value));
        row.put("value", previewValue(value));
        target.add(row);
    }

    private String detectParamType(Object value) {
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        return "string";
    }

    private Object previewValue(Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return toJsonString(value);
        }
        return value;
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

    private Object coerceBody(Object rendered, boolean preferJsonObject) {
        if (!(rendered instanceof String text)) {
            return rendered;
        }
        if (!preferJsonObject) {
            return text;
        }
        String normalized = text.trim();
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (!(normalized.startsWith("{") || normalized.startsWith("["))) {
            return text;
        }
        try {
            return objectMapper.readValue(normalized, Object.class);
        } catch (Exception ex) {
            return text;
        }
    }

    private Object invokeRemoteApi(String requestUrl, String method, Map<String, Object> headers, Object requestBody)
            throws TaskException {
        try {
            RestClient client = buildRestClient();
            return switch (method) {
                case "GET" -> client.get().uri(requestUrl).headers(httpHeaders -> applyHeaders(httpHeaders, headers)).retrieve().body(Object.class);
                case "DELETE" -> client.delete().uri(requestUrl).headers(httpHeaders -> applyHeaders(httpHeaders, headers)).retrieve().body(Object.class);
                case "PUT" -> client.put().uri(requestUrl).headers(httpHeaders -> applyHeaders(httpHeaders, headers)).body(requestBody).retrieve().body(Object.class);
                case "PATCH" -> client.patch().uri(requestUrl).headers(httpHeaders -> applyHeaders(httpHeaders, headers)).body(requestBody).retrieve().body(Object.class);
                default -> client.post().uri(requestUrl).headers(httpHeaders -> applyHeaders(httpHeaders, headers)).body(requestBody).retrieve().body(Object.class);
            };
        } catch (RestClientResponseException ex) {
            String body = shorten(ex.getResponseBodyAsString(), 500);
            logger.warn("连接器接口调用失败: status={}, url={}, body={}", ex.getStatusCode().value(), requestUrl, body);
            throw new TaskException(
                    "外部接口调用失败，HTTP " + ex.getStatusCode().value() + " " + body,
                    TaskException.Code.UNKNOWN,
                    ex);
        } catch (Exception ex) {
            logger.warn("连接器接口调用失败: url={}, error={}", requestUrl, ex.getMessage(), ex);
            throw new TaskException("外部接口调用失败: " + ex.getMessage(), TaskException.Code.UNKNOWN, ex);
        }
    }

    private RestClient buildRestClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(DEFAULT_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private String buildRequestUrl(String baseUrl, String pathOrUrl, Map<String, Object> queryParams) throws TaskException {
        if (!StringUtils.hasText(baseUrl) && !StringUtils.hasText(pathOrUrl)) {
            throw new TaskException("请求地址不能为空", TaskException.Code.UNKNOWN);
        }
        String normalizedPath = text(pathOrUrl);
        if (!StringUtils.hasText(normalizedPath)) {
            normalizedPath = "/";
        }
        String finalUrl;
        if (normalizedPath.startsWith("http://") || normalizedPath.startsWith("https://")) {
            finalUrl = normalizedPath;
        } else {
            String normalizedBaseUrl = text(baseUrl).replaceAll("/+$", "");
            if (!normalizedPath.startsWith("/")) {
                normalizedPath = "/" + normalizedPath;
            }
            finalUrl = normalizedBaseUrl + normalizedPath;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(finalUrl);
        queryParams.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null) {
                builder.queryParam(key, stringifyScalar(value));
            }
        });
        return builder.build(true).toUriString();
    }

    private String buildFormBody(Map<String, Object> formValues) {
        List<String> pairs = new ArrayList<>();
        formValues.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || value == null) {
                return;
            }
            pairs.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(stringifyScalar(value), StandardCharsets.UTF_8));
        });
        return String.join("&", pairs);
    }

    private void applyHeaders(HttpHeaders target, Map<String, Object> headers) {
        headers.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null) {
                target.set(key, stringifyScalar(value));
            }
        });
    }

    private Map<String, Object> maskHeaders(Map<String, Object> headers) {
        Map<String, Object> result = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(key)
                    || lower.contains("token")
                    || lower.contains("secret")
                    || lower.contains("key")) {
                result.put(key, "***");
            } else {
                result.put(key, value);
            }
        });
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

    private Map<String, Object> normalizeObjectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private String normalizeHttpMethod(String method, String defaultValue) {
        String normalized = text(method).toUpperCase(Locale.ROOT);
        return List.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(normalized) ? normalized : defaultValue;
    }

    private String toJsonString(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String shorten(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String stringifyScalar(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String generateObjectId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record ExecutionDebugResult(
            String method,
            String requestUrl,
            Map<String, Object> requestHeaders,
            Map<String, Object> requestQuery,
            Object requestBody,
            Object rawResponse,
            String jsonStr,
            List<Map<String, Object>> returnInfo,
            Object result) {}

    public record AuthExecutionDebugResult(
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

    private record CachedAuthResult(AuthExecutionDebugResult result, long expiresAt) {
        private boolean expired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
