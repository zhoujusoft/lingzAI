package lingzhou.agent.backend.capability.modelruntime;

import java.time.Duration;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class ModelRuntimeClientFactory {

    private final ModelRuntimeConfigResolver modelRuntimeConfigResolver;

    public ModelRuntimeClientFactory(ModelRuntimeConfigResolver modelRuntimeConfigResolver) {
        this.modelRuntimeConfigResolver = modelRuntimeConfigResolver;
    }

    public ChatRuntimeBundle createChatBundle() {
        return createChatBundle(null);
    }

    public ChatRuntimeBundle createChatBundle(ToolCallingManager toolCallingManager) {
        ModelRuntimeConfigResolver.ResolvedChatModelConfig config = modelRuntimeConfigResolver.resolveChatConfig();
        OpenAiChatModel.Builder builder = OpenAiChatModel.builder()
                .openAiApi(buildChatOpenAiApi(config.baseUrl(), config.apiKey(), config.completionsPath()))
                .defaultOptions(buildChatOptions(config));
        if (toolCallingManager != null) {
            builder.toolCallingManager(toolCallingManager);
        }
        OpenAiChatModel chatModel = builder.build();
        ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);
        if (StringUtils.hasText(config.systemPrompt())) {
            chatClientBuilder.defaultSystem(config.systemPrompt());
        }
        return new ChatRuntimeBundle(chatClientBuilder.build(), chatModel, config);
    }

    public EmbeddingModel createEmbeddingModel() {
        ModelRuntimeConfigResolver.ResolvedEmbeddingModelConfig config = modelRuntimeConfigResolver.resolveEmbeddingConfig();
        if (!StringUtils.hasText(config.baseUrl())
                || !StringUtils.hasText(config.apiKey())
                || !StringUtils.hasText(config.model())) {
            throw new IllegalStateException("Embedding model configuration is incomplete.");
        }
        OpenAiApi openAiApi = buildEmbeddingOpenAiApi(config.baseUrl(), config.apiKey(), config.embeddingsPath());
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder().model(config.model()).build();
        if (config.dimensions() != null && config.dimensions() > 0) {
            options.setDimensions(config.dimensions());
        }
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.NONE, options);
    }

    private OpenAiApi buildChatOpenAiApi(String baseUrl, String apiKey, String completionsPath) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(builder);
        if (StringUtils.hasText(completionsPath)) {
            apiBuilder.completionsPath(completionsPath);
        }
        return apiBuilder.build();
    }

    private OpenAiApi buildEmbeddingOpenAiApi(String baseUrl, String apiKey, String embeddingsPath) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(builder);
        if (StringUtils.hasText(embeddingsPath)) {
            apiBuilder.embeddingsPath(embeddingsPath);
        }
        return apiBuilder.build();
    }

    private OpenAiChatOptions buildChatOptions(ModelRuntimeConfigResolver.ResolvedChatModelConfig config) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(config.model())
                .maxTokens(config.maxTokens())
                .streamUsage(false)
                .temperature(config.temperature());
        if (config.enableThinking() != null) {
            builder.extraBody(Map.of(
                    "chat_template_kwargs",
                    Map.of("enable_thinking", config.enableThinking())));
        }
        return builder.build();
    }

    public record ChatRuntimeBundle(
            ChatClient chatClient,
            OpenAiChatModel chatModel,
            ModelRuntimeConfigResolver.ResolvedChatModelConfig config) {}
}
