package lingzhou.agent.backend.capability.rag.ranking;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver;
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
public class AlibabaRerankService {

    static final String DASHSCOPE_HOST = "dashscope.aliyuncs.com";
    static final String DASHSCOPE_DEFAULT_PATH = "/api/v1/services/rerank/text-rerank/text-rerank";

    private static final Logger log = LoggerFactory.getLogger(AlibabaRerankService.class);

    private final ModelRuntimeConfigResolver modelRuntimeConfigResolver;

    public AlibabaRerankService(ModelRuntimeConfigResolver modelRuntimeConfigResolver) {
        this.modelRuntimeConfigResolver = modelRuntimeConfigResolver;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(modelRuntimeConfigResolver.resolveRerankConfig().enabled());
    }

    public boolean allowFallbackToRrf() {
        return !Boolean.FALSE.equals(modelRuntimeConfigResolver.resolveRerankConfig().fallbackRrf());
    }

    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        ModelRuntimeConfigResolver.ResolvedRerankModelConfig config = modelRuntimeConfigResolver.resolveRerankConfig();
        if (!Boolean.TRUE.equals(config.enabled())) {
            return List.of();
        }
        if (!StringUtils.hasText(query) || documents == null || documents.isEmpty()) {
            return List.of();
        }
        RestClient restClient = buildClient(config);
        if (restClient == null) {
            throw new IllegalStateException("Rerank 配置不完整，无法调用重排序模型。");
        }

        Object response;
        try {
            response = restClient
                    .post()
                    .uri(resolvePath(config))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(config, query, documents, topN))
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Rerank HTTP " + ex.getStatusCode().value() + "："
                            + shorten(ex.getResponseBodyAsString(), 300),
                    ex);
        }
        return parseResults(response);
    }

    private RestClient buildClient(ModelRuntimeConfigResolver.ResolvedRerankModelConfig config) {
        String normalizedBaseUrl = normalizeBaseUrl(config.baseUrl());
        if (!StringUtils.hasText(normalizedBaseUrl) || !StringUtils.hasText(config.apiKey())) {
            return null;
        }

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        int timeoutMs = config.timeoutMs() == null || config.timeoutMs() <= 0 ? 1200 : config.timeoutMs();
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .baseUrl(normalizedBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .requestFactory(requestFactory)
                .build();
    }

    Map<String, Object> buildRequestBody(
            ModelRuntimeConfigResolver.ResolvedRerankModelConfig config,
            String query,
            List<String> documents,
            int topN) {
        int safeTopN = Math.max(1, Math.min(topN, documents.size()));
        RerankProtocol protocol = resolveProtocol(config);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());

        if (protocol == RerankProtocol.VLLM) {
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", safeTopN);
            body.put("return_documents", Boolean.TRUE);
            return body;
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("top_n", safeTopN);
        parameters.put("return_documents", Boolean.TRUE);
        body.put("parameters", parameters);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("documents", documents);
        body.put("input", input);
        return body;
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/compatible-mode")) {
            return normalized.substring(0, normalized.length() - "/compatible-mode".length());
        }
        if (normalized.endsWith("/compatible-api")) {
            return normalized.substring(0, normalized.length() - "/compatible-api".length());
        }
        return normalized;
    }

    static String resolvePath(ModelRuntimeConfigResolver.ResolvedRerankModelConfig config) {
        String configured = StringUtils.hasText(config.path()) ? config.path().trim() : DASHSCOPE_DEFAULT_PATH;
        if (!configured.startsWith("/")) {
            configured = "/" + configured;
        }
        String baseUrl = normalizeBaseUrl(config.baseUrl());
        boolean dashscope = StringUtils.hasText(baseUrl) && baseUrl.contains(DASHSCOPE_HOST);
        if (dashscope && ("/v1/rerank".equals(configured) || "/v1/reranks".equals(configured))) {
            return DASHSCOPE_DEFAULT_PATH;
        }
        return configured;
    }

    private RerankProtocol resolveProtocol(ModelRuntimeConfigResolver.ResolvedRerankModelConfig config) {
        if (StringUtils.hasText(config.protocol())) {
            return RerankProtocol.from(config.protocol());
        }
        if ("VLLM".equalsIgnoreCase(config.adapterType()) || "vllm".equalsIgnoreCase(config.adapterType())) {
            return RerankProtocol.VLLM;
        }
        String path = resolvePath(config);
        if ("/v1/rerank".equals(path) || "/v1/reranks".equals(path)) {
            return RerankProtocol.VLLM;
        }
        log.warn("未显式配置 Rerank 协议，按 DashScope 兼容格式处理：baseUrl={}, path={}", shorten(config.baseUrl(), 120), path);
        return RerankProtocol.DASHSCOPE;
    }

    private List<RerankResult> parseResults(Object rawResponse) {
        if (!(rawResponse instanceof Map<?, ?> responseMap)) {
            throw new IllegalStateException("Rerank 返回格式异常。");
        }

        Object results = responseMap.get("results");
        if (results instanceof List<?> resultList) {
            return parseResultList(resultList);
        }

        Object data = responseMap.get("data");
        if (data instanceof List<?> dataList) {
            return parseResultList(dataList);
        }

        Object output = responseMap.get("output");
        if (output instanceof Map<?, ?> outputMap) {
            Object nestedResults = outputMap.get("results");
            if (nestedResults instanceof List<?> resultList) {
                return parseResultList(resultList);
            }
        }

        throw new IllegalStateException("Rerank 返回缺少 results 字段。");
    }

    private List<RerankResult> parseResultList(List<?> rawResults) {
        List<RerankResult> results = new ArrayList<>();
        for (Object rawItem : rawResults) {
            if (!(rawItem instanceof Map<?, ?> item)) {
                continue;
            }
            Integer index = asInteger(item.get("index"));
            if (index == null) {
                index = asInteger(item.get("document_id"));
            }
            if (index == null) {
                continue;
            }
            Double score = asDouble(item.get("relevance_score"));
            if (score == null) {
                score = asDouble(item.get("score"));
            }
            if (score == null) {
                score = 0D;
            }
            results.add(new RerankResult(index, score));
        }
        return results;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String shorten(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "empty response body";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private enum RerankProtocol {
        DASHSCOPE,
        VLLM;

        static RerankProtocol from(String value) {
            String normalized = StringUtils.trimWhitespace(value);
            if (!StringUtils.hasText(normalized)) {
                return DASHSCOPE;
            }
            if ("vllm".equalsIgnoreCase(normalized) || "openai".equalsIgnoreCase(normalized)
                    || "openai-rerank".equalsIgnoreCase(normalized) || "vllm-openai".equalsIgnoreCase(normalized)) {
                return VLLM;
            }
            return DASHSCOPE;
        }
    }

    public record RerankResult(int index, double score) {}
}
