package lingzhou.agent.backend.app;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties({
    ModelProviderProperties.class,
    EmbeddingModelProperties.class,
    RagElasticsearchProperties.class,
    RagRetrievalProperties.class,
    RagRerankProperties.class,
    RagQaProperties.class
})
public class RagVectorConfig {

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
