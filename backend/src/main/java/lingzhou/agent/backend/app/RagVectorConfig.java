package lingzhou.agent.backend.app;

import java.time.Duration;
import java.util.Base64;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties({
    EmbeddingModelProperties.class,
    RagElasticsearchProperties.class,
    RagRetrievalProperties.class,
    RagRerankProperties.class,
    RagQaProperties.class
})
public class RagVectorConfig {

    @Bean
    public EmbeddingModel embeddingModel(ChatModelProperties chatProperties, EmbeddingModelProperties embeddingProps) {
        String baseUrl = StringUtils.hasText(embeddingProps.getBaseUrl())
                ? embeddingProps.getBaseUrl()
                : chatProperties.getBaseUrl();
        String apiKey = StringUtils.hasText(embeddingProps.getApiKey())
                ? embeddingProps.getApiKey()
                : chatProperties.getApiKey();
        String model = StringUtils.hasText(embeddingProps.getModel())
                ? embeddingProps.getModel()
                : chatProperties.getModel();

        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(model)) {
            throw new IllegalStateException("Embedding model configuration is incomplete.");
        }

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(120));

        org.springframework.web.client.RestClient.Builder builder =
                org.springframework.web.client.RestClient.builder().requestFactory(requestFactory);

        OpenAiApi.Builder apiBuilder =
                OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).restClientBuilder(builder);
        if (StringUtils.hasText(embeddingProps.getEmbeddingsPath())) {
            apiBuilder.embeddingsPath(embeddingProps.getEmbeddingsPath());
        }

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder().model(model).build();
        if (embeddingProps.getDimensions() != null && embeddingProps.getDimensions() > 0) {
            options.setDimensions(embeddingProps.getDimensions());
        }

        return new OpenAiEmbeddingModel(apiBuilder.build(), MetadataMode.NONE, options);
    }

    @Bean
    public RestClient ragElasticsearchRestClient(RagElasticsearchProperties properties) {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("Elasticsearch baseUrl is required for RAG indexing.");
        }

        RestClientBuilder builder = RestClient.builder(HttpHost.create(properties.getBaseUrl()))
                .setDefaultHeaders(buildAuthorizationHeaders(properties));
        return builder.build();
    }

    @Bean
    public ElasticsearchTransport ragElasticsearchTransport(RestClient ragElasticsearchRestClient) {
        return new RestClientTransport(ragElasticsearchRestClient, new JacksonJsonpMapper());
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport ragElasticsearchTransport) {
        return new ElasticsearchClient(ragElasticsearchTransport);
    }

    private Header[] buildAuthorizationHeaders(RagElasticsearchProperties properties) {
        if (StringUtils.hasText(properties.getApiKey())) {
            return new Header[] {new BasicHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + properties.getApiKey())};
        }

        return new Header[0];
    }
}
