package lingzhou.agent.backend.capability.modelruntime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
public class ModelRuntimeClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(ModelRuntimeClientFactory.class);

    private final ModelRuntimeConfigResolver modelRuntimeConfigResolver;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;
    private final ObjectProvider<WebClient.Builder> webClientBuilderProvider;

    public ModelRuntimeClientFactory(
            ModelRuntimeConfigResolver modelRuntimeConfigResolver,
            ObjectMapper objectMapper,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
        this.modelRuntimeConfigResolver = modelRuntimeConfigResolver;
        this.objectMapper = objectMapper;
        this.restClientBuilderProvider = restClientBuilderProvider;
        this.webClientBuilderProvider = webClientBuilderProvider;
    }

    public ChatRuntimeBundle createChatBundle() {
        return createChatBundle((Long) null, null, true);
    }

    public ChatRuntimeBundle createChatBundle(ToolCallingManager toolCallingManager) {
        return createChatBundle((Long) null, toolCallingManager, true);
    }

    public ChatRuntimeBundle createChatBundle(Long chatModelId) {
        return createChatBundle(chatModelId, null, true);
    }

    public ChatRuntimeBundle createChatBundle(Long chatModelId, ToolCallingManager toolCallingManager) {
        return createChatBundle(chatModelId, toolCallingManager, true);
    }

    public ChatRuntimeBundle createChatBundleWithoutDefaultSystem() {
        return createChatBundle((Long) null, null, false);
    }

    public ChatRuntimeBundle createChatBundleWithoutDefaultSystem(ToolCallingManager toolCallingManager) {
        return createChatBundle((Long) null, toolCallingManager, false);
    }

    public ChatRuntimeBundle createChatBundleWithoutDefaultSystem(Long chatModelId) {
        return createChatBundle(chatModelId, null, false);
    }

    public ChatRuntimeBundle createChatBundleWithoutDefaultSystem(
            Long chatModelId, ToolCallingManager toolCallingManager) {
        return createChatBundle(chatModelId, toolCallingManager, false);
    }

    public ChatRuntimeBundle createChatBundle(ModelRuntimeConfigResolver.ResolvedChatModelConfig config) {
        return createChatBundleFromConfig(config, null, true);
    }

    public ChatRuntimeBundle createChatBundleWithoutDefaultSystem(
            ModelRuntimeConfigResolver.ResolvedChatModelConfig config) {
        return createChatBundleFromConfig(config, null, false);
    }

    public void validateChatConnectivity(
            ModelRuntimeConfigResolver.ResolvedChatModelConfig config, String userMessage) {
        String validationMessage = StringUtils.hasText(userMessage) ? userMessage.trim() : "ping";
        createChatBundleWithoutDefaultSystem(config)
                .chatClient()
                .prompt()
                .user(validationMessage)
                .call()
                .content();
    }

    private ChatRuntimeBundle createChatBundle(
            Long chatModelId, ToolCallingManager toolCallingManager, boolean includeDefaultSystemPrompt) {
        ModelRuntimeConfigResolver.ResolvedChatModelConfig config = modelRuntimeConfigResolver.resolveChatConfig(chatModelId);
        return createChatBundleFromConfig(config, toolCallingManager, includeDefaultSystemPrompt);
    }

    private ChatRuntimeBundle createChatBundleFromConfig(
            ModelRuntimeConfigResolver.ResolvedChatModelConfig config,
            ToolCallingManager toolCallingManager,
            boolean includeDefaultSystemPrompt) {
        OpenAiChatModel.Builder builder = OpenAiChatModel.builder()
                .openAiApi(buildChatOpenAiApi(config))
                .defaultOptions(buildChatOptions(config));
        if (toolCallingManager != null) {
            builder.toolCallingManager(toolCallingManager);
        }
        OpenAiChatModel chatModel = builder.build();
        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);
        if (includeDefaultSystemPrompt && StringUtils.hasText(config.systemPrompt())) {
            chatClientBuilder.defaultSystem(config.systemPrompt());
        }
        return new ChatRuntimeBundle(chatClientBuilder.build(), chatModel, config);
    }

    public EmbeddingModel createEmbeddingModel() {
        ModelRuntimeConfigResolver.ResolvedEmbeddingModelConfig config =
                modelRuntimeConfigResolver.resolveEmbeddingConfig();
        if (!StringUtils.hasText(config.baseUrl()) || !StringUtils.hasText(config.model())) {
            throw new IllegalStateException("Embedding model configuration is incomplete.");
        }
        OpenAiApi openAiApi = buildEmbeddingOpenAiApi(config.baseUrl(), config.apiKey(), config.embeddingsPath());
        OpenAiEmbeddingOptions options =
                OpenAiEmbeddingOptions.builder().model(config.model()).build();
        if (config.dimensions() != null && config.dimensions() > 0) {
            options.setDimensions(config.dimensions());
        }
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.NONE, options);
    }

    private OpenAiApi buildChatOpenAiApi(ModelRuntimeConfigResolver.ResolvedChatModelConfig config) {
        String normalizedBaseUrl = normalizeOpenAiBaseUrl(config.baseUrl());
        String resolvedCompletionsPath = resolveOpenAiPath(normalizedBaseUrl, config.completionsPath());
        MultiValueMap<String, String> headers = buildOpenAiHeaders();
        ApiKey resolvedApiKey = resolveApiKey(config.apiKey());
        RestClient.Builder restClientBuilder =
                applyHttpTimeouts(restClientBuilderProvider.getIfAvailable(RestClient::builder));
        WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
        webClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE);
        return new OpenAiApi(
                normalizedBaseUrl,
                resolvedApiKey,
                headers,
                resolvedCompletionsPath,
                "/v1/embeddings",
                restClientBuilder,
                webClientBuilder,
                RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER) {
            @Override
            public org.springframework.http.ResponseEntity<OpenAiApi.ChatCompletion> chatCompletionEntity(
                    OpenAiApi.ChatCompletionRequest chatRequest, MultiValueMap<String, String> additionalHttpHeader) {
                OpenAiApi.ChatCompletionRequest patchedRequest = normalizeVllmRequest(config, chatRequest);
                logOpenAiRequest(config, patchedRequest, false);
                try {
                    return super.chatCompletionEntity(patchedRequest, additionalHttpHeader);
                } catch (WebClientResponseException ex) {
                    logOpenAiError(config, ex);
                    throw ex;
                }
            }

            @Override
            public Flux<OpenAiApi.ChatCompletionChunk> chatCompletionStream(
                    OpenAiApi.ChatCompletionRequest chatRequest, MultiValueMap<String, String> additionalHttpHeader) {
                OpenAiApi.ChatCompletionRequest patchedRequest = normalizeVllmRequest(config, chatRequest);
                logOpenAiRequest(config, patchedRequest, true);
                Flux<OpenAiApi.ChatCompletionChunk> streamFlux = isVllmAdapter(config)
                        ? streamVllmWithJdkClient(
                                config,
                                normalizedBaseUrl,
                                resolvedCompletionsPath,
                                headers,
                                additionalHttpHeader,
                                resolvedApiKey,
                                patchedRequest)
                        : super.chatCompletionStream(patchedRequest, additionalHttpHeader);
                return streamFlux.doOnError(error -> {
                    if (error instanceof WebClientResponseException ex) {
                        logOpenAiError(config, ex);
                    }
                });
            }
        };
    }

    private OpenAiApi buildEmbeddingOpenAiApi(String baseUrl, String apiKey, String embeddingsPath) {
        String normalizedBaseUrl = normalizeOpenAiBaseUrl(baseUrl);
        String resolvedEmbeddingsPath = resolveOpenAiPath(normalizedBaseUrl, embeddingsPath);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        OpenAiApi.Builder apiBuilder =
                OpenAiApi.builder().baseUrl(normalizedBaseUrl).restClientBuilder(restClientBuilder);
        if (StringUtils.hasText(apiKey)) {
            apiBuilder.apiKey(apiKey);
        } else {
            apiBuilder.apiKey("");
        }
        if (StringUtils.hasText(resolvedEmbeddingsPath)) {
            apiBuilder.embeddingsPath(resolvedEmbeddingsPath);
        }
        return apiBuilder.build();
    }

    private OpenAiChatOptions buildChatOptions(ModelRuntimeConfigResolver.ResolvedChatModelConfig config) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(config.model())
                .maxTokens(config.maxTokens())
                .streamUsage(true)
                .temperature(config.temperature());
        Map<String, Object> extraBody = buildVllmExtraBody(config);
        if (!extraBody.isEmpty()) {
            builder.extraBody(extraBody);
        }
        OpenAiChatOptions options = builder.build();
        options.setStreamOptions(OpenAiApi.ChatCompletionRequest.StreamOptions.INCLUDE_USAGE);
        return options;
    }

    private OpenAiApi.ChatCompletionRequest normalizeVllmRequest(
            ModelRuntimeConfigResolver.ResolvedChatModelConfig config, OpenAiApi.ChatCompletionRequest request) {
        OpenAiApi.ChatCompletionRequest patchedRequest = ensureVllmExtraBody(config, request);
        if (patchedRequest == null || !isVllmAdapter(config)) {
            return patchedRequest;
        }
        return normalizeVllmToolCallArguments(patchedRequest);
    }

    private Map<String, Object> buildVllmExtraBody(ModelRuntimeConfigResolver.ResolvedChatModelConfig config) {
        if (config == null || !"VLLM".equalsIgnoreCase(config.adapterType())) {
            return Map.of();
        }
        boolean enableThinking = Boolean.TRUE.equals(config.enableThinking());
        return Map.of(
                "enable_thinking", enableThinking, "chat_template_kwargs", Map.of("enable_thinking", enableThinking));
    }

    private OpenAiApi.ChatCompletionRequest ensureVllmExtraBody(
            ModelRuntimeConfigResolver.ResolvedChatModelConfig config, OpenAiApi.ChatCompletionRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> defaults = buildVllmExtraBody(config);
        if (defaults.isEmpty()) {
            return request;
        }
        Map<String, Object> mergedExtraBody = new LinkedHashMap<>(defaults);
        if (request.extraBody() != null && !request.extraBody().isEmpty()) {
            mergedExtraBody.putAll(request.extraBody());
        }
        if (mergedExtraBody.equals(request.extraBody())) {
            return request;
        }
        return new OpenAiApi.ChatCompletionRequest(
                request.messages(),
                request.model(),
                request.store(),
                request.metadata(),
                request.frequencyPenalty(),
                request.logitBias(),
                request.logprobs(),
                request.topLogprobs(),
                request.maxTokens(),
                request.maxCompletionTokens(),
                request.n(),
                request.outputModalities(),
                request.audioParameters(),
                request.presencePenalty(),
                request.responseFormat(),
                request.seed(),
                request.serviceTier(),
                request.stop(),
                request.stream(),
                request.streamOptions(),
                request.temperature(),
                request.topP(),
                request.tools(),
                request.toolChoice(),
                request.parallelToolCalls(),
                request.user(),
                request.reasoningEffort(),
                request.webSearchOptions(),
                request.verbosity(),
                request.promptCacheKey(),
                request.safetyIdentifier(),
                Map.copyOf(mergedExtraBody));
    }

    private OpenAiApi.ChatCompletionRequest normalizeVllmToolCallArguments(OpenAiApi.ChatCompletionRequest request) {
        try {
            JsonNode requestNode = objectMapper.valueToTree(request);
            if (!(requestNode instanceof ObjectNode requestObject)) {
                return request;
            }
            if (!normalizeVllmToolCallArguments(requestObject)) {
                return request;
            }
            return objectMapper.treeToValue(requestObject, OpenAiApi.ChatCompletionRequest.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("vLLM tool call arguments 归一化失败", ex);
        }
    }

    static boolean normalizeVllmToolCallArguments(ObjectNode requestObject) {
        if (requestObject == null) {
            return false;
        }
        JsonNode messagesNode = requestObject.get("messages");
        if (!(messagesNode instanceof ArrayNode messagesArray)) {
            return false;
        }
        boolean changed = false;
        for (JsonNode messageNode : messagesArray) {
            if (!(messageNode instanceof ObjectNode messageObject)) {
                continue;
            }
            changed |= normalizeVllmToolCallArgumentsInMessage(messageObject, "tool_calls");
            changed |= normalizeVllmToolCallArgumentsInMessage(messageObject, "toolCalls");
        }
        return changed;
    }

    private static boolean normalizeVllmToolCallArgumentsInMessage(ObjectNode messageObject, String fieldName) {
        JsonNode toolCallsNode = messageObject.get(fieldName);
        if (!(toolCallsNode instanceof ArrayNode toolCallsArray)) {
            return false;
        }
        boolean changed = false;
        for (JsonNode toolCallNode : toolCallsArray) {
            if (!(toolCallNode instanceof ObjectNode toolCallObject)) {
                continue;
            }
            JsonNode functionNode = toolCallObject.get("function");
            if (!(functionNode instanceof ObjectNode functionObject)) {
                continue;
            }
            JsonNode argumentsNode = functionObject.get("arguments");
            String normalizedArguments = normalizeVllmToolCallArgumentsValue(argumentsNode);
            if (argumentsNode != null && argumentsNode.isTextual() && normalizedArguments.equals(argumentsNode.asText())) {
                continue;
            }
            functionObject.put("arguments", normalizedArguments);
            changed = true;
        }
        return changed;
    }

    private static String normalizeVllmToolCallArgumentsValue(JsonNode argumentsNode) {
        if (argumentsNode == null || argumentsNode.isNull()) {
            return "{}";
        }
        if (argumentsNode.isTextual()) {
            String arguments = argumentsNode.asText();
            return StringUtils.hasText(arguments) ? arguments : "{}";
        }
        return argumentsNode.toString();
    }

    private MultiValueMap<String, String> buildOpenAiHeaders() {
        LinkedMultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add(HttpHeaders.USER_AGENT, "Lingzhou-Agent/1.0");
        return headers;
    }

    private ApiKey resolveApiKey(String apiKey) {
        if (StringUtils.hasText(apiKey)) {
            return new SimpleApiKey(apiKey.trim());
        }
        return new NoopApiKey();
    }

    private RestClient.Builder applyHttpTimeouts(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        return builder.requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private String normalizeOpenAiBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/v1")) {
            return normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    private String resolveOpenAiPath(String normalizedBaseUrl, String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        String resolved = path.trim();
        if (!resolved.startsWith("/")) {
            resolved = "/" + resolved;
        }
        if (StringUtils.hasText(normalizedBaseUrl)
                && normalizedBaseUrl.endsWith("/v1")
                && resolved.startsWith("/v1/")) {
            resolved = resolved.substring(3);
            if (!resolved.startsWith("/")) {
                resolved = "/" + resolved;
            }
        }
        return resolved;
    }

    private void logOpenAiRequest(
            ModelRuntimeConfigResolver.ResolvedChatModelConfig config,
            OpenAiApi.ChatCompletionRequest request,
            boolean stream) {
        //        try {
        //            logger.debug(
        //                    "OpenAI-compatible request: modelId={}, provider={}, model={}, stream={}, body={}",
        //                    config.modelId(),
        //                    config.provider(),
        //                    config.model(),
        //                    stream,
        //                    objectMapper.writeValueAsString(request));
        //        } catch (JsonProcessingException ex) {
        //            logger.warn(
        //                    "OpenAI-compatible 请求日志序列化失败：modelId={}, provider={}, model={}, error={}",
        //                    config.modelId(),
        //                    config.provider(),
        //                    config.model(),
        //                    ex.getMessage());
        //        }
    }

    private void logOpenAiError(
            ModelRuntimeConfigResolver.ResolvedChatModelConfig config, WebClientResponseException ex) {
        logger.error(
                "OpenAI-compatible error: modelId={}, provider={}, model={}, status={}, body={}",
                config.modelId(),
                config.provider(),
                config.model(),
                ex.getStatusCode().value(),
                ex.getResponseBodyAsString());
    }

    private Flux<OpenAiApi.ChatCompletionChunk> streamVllmWithJdkClient(
            ModelRuntimeConfigResolver.ResolvedChatModelConfig config,
            String baseUrl,
            String completionsPath,
            MultiValueMap<String, String> defaultHeaders,
            MultiValueMap<String, String> additionalHeaders,
            ApiKey apiKey,
            OpenAiApi.ChatCompletionRequest request) {
        return Flux.<OpenAiApi.ChatCompletionChunk>create(sink -> {
                    HttpClient httpClient = HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1)
                            .connectTimeout(Duration.ofSeconds(20))
                            .build();
                    String requestBody;
                    try {
                        requestBody = objectMapper.writeValueAsString(request);
                    } catch (JsonProcessingException ex) {
                        sink.error(ex);
                        return;
                    }
                    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + completionsPath))
                            .timeout(Duration.ofSeconds(120))
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .header(
                                    HttpHeaders.ACCEPT,
                                    MediaType.TEXT_EVENT_STREAM_VALUE + ", " + MediaType.APPLICATION_JSON_VALUE)
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody));
                    applyHeaders(requestBuilder, defaultHeaders);
                    applyHeaders(requestBuilder, additionalHeaders);
                    if (apiKey instanceof SimpleApiKey simpleApiKey && StringUtils.hasText(simpleApiKey.getValue())) {
                        requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + simpleApiKey.getValue());
                    }
                    try {
                        HttpResponse<InputStream> response =
                                httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
                        if (response.statusCode() >= 400) {
                            byte[] bodyBytes = response.body().readAllBytes();
                            throw toWebClientResponseException(response, bodyBytes);
                        }
                        emitSseChunks(response.body(), sink);
                    } catch (Exception ex) {
                        sink.error(ex);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void emitSseChunks(
            InputStream inputStream, reactor.core.publisher.FluxSink<OpenAiApi.ChatCompletionChunk> sink)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null && !sink.isCancelled()) {
                if (!StringUtils.hasText(line) || !line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if ("[DONE]".equals(payload)) {
                    sink.complete();
                    return;
                }
                if (!StringUtils.hasText(payload)) {
                    continue;
                }
                String normalizedPayload = normalizeVllmFinishReasonPayload(objectMapper, payload);
                OpenAiApi.ChatCompletionChunk chunk =
                        objectMapper.readValue(normalizedPayload, OpenAiApi.ChatCompletionChunk.class);
                chunk = normalizeVllmChunk(chunk, normalizedPayload);
                if (chunk == null) {
                    continue;
                }
                sink.next(chunk);
            }
            if (!sink.isCancelled()) {
                sink.complete();
            }
        }
    }

    static OpenAiApi.ChatCompletionChunk normalizeVllmChunk(OpenAiApi.ChatCompletionChunk chunk, String payload) {
        if (chunk == null) {
            return null;
        }
        if (chunk.choices() != null) {
            return chunk;
        }
        logger.debug(
                "vLLM 流式 chunk 缺少 choices，已归一化为空列表：model={}, object={}, usageAvailable={}, payload={}",
                chunk.model(),
                chunk.object(),
                chunk.usage() != null,
                shrinkPayload(payload));
        return new OpenAiApi.ChatCompletionChunk(
                chunk.id(),
                List.of(),
                chunk.created(),
                chunk.model(),
                chunk.serviceTier(),
                chunk.systemFingerprint(),
                chunk.object(),
                chunk.usage());
    }

    static String normalizeVllmFinishReasonPayload(ObjectMapper objectMapper, String payload) {
        if (!StringUtils.hasText(payload) || !payload.contains("\"finish_reason\"")) {
            return payload;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!(root instanceof ObjectNode rootObject)) {
                return payload;
            }
            JsonNode choicesNode = rootObject.get("choices");
            if (!(choicesNode instanceof ArrayNode choicesArray)) {
                return payload;
            }
            boolean changed = false;
            for (JsonNode choice : choicesArray) {
                if (!(choice instanceof ObjectNode choiceObject)) {
                    continue;
                }
                JsonNode finishReasonNode = choiceObject.get("finish_reason");
                if (finishReasonNode == null || finishReasonNode.isNull()) {
                    continue;
                }
                if (finishReasonNode.isTextual() && !StringUtils.hasText(finishReasonNode.asText())) {
                    choiceObject.putNull("finish_reason");
                    changed = true;
                }
            }
            if (!changed) {
                return payload;
            }
            return objectMapper.writeValueAsString(rootObject);
        } catch (Exception ex) {
            logger.debug("vLLM 流式 chunk 预归一化失败，按原始 payload 继续解析：error={}", ex.getMessage());
            return payload;
        }
    }

    private static String shrinkPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return "";
        }
        String normalized = payload.replace('\n', ' ').trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    private WebClientResponseException toWebClientResponseException(
            HttpResponse<InputStream> response, byte[] bodyBytes) {
        HttpHeaders responseHeaders = new HttpHeaders();
        response.headers().map().forEach((key, values) -> responseHeaders.put(key, new ArrayList<>(values)));
        return WebClientResponseException.create(response.statusCode(), "", responseHeaders, bodyBytes, null, null);
    }

    private void applyHeaders(HttpRequest.Builder requestBuilder, MultiValueMap<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach((name, values) -> {
            if (!StringUtils.hasText(name) || values == null) {
                return;
            }
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    requestBuilder.header(name, value);
                }
            }
        });
    }

    private boolean isVllmAdapter(ModelRuntimeConfigResolver.ResolvedChatModelConfig config) {
        return config != null && "VLLM".equalsIgnoreCase(config.adapterType());
    }

    public record ChatRuntimeBundle(
            ChatClient chatClient,
            OpenAiChatModel chatModel,
            ModelRuntimeConfigResolver.ResolvedChatModelConfig config) {}
}
