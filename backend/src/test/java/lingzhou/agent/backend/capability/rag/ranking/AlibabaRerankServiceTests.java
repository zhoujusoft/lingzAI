package lingzhou.agent.backend.capability.rag.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.app.RagRerankProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

class AlibabaRerankServiceTests {

    @Test
    void normalizeBaseUrlRemovesDashscopeCompatibleSuffix() {
        assertThat(AlibabaRerankService.normalizeBaseUrl("https://dashscope.aliyuncs.com/compatible-mode"))
                .isEqualTo("https://dashscope.aliyuncs.com");
        assertThat(AlibabaRerankService.normalizeBaseUrl("https://dashscope.aliyuncs.com/compatible-api/"))
                .isEqualTo("https://dashscope.aliyuncs.com");
    }

    @Test
    void resolvePathRewritesLegacyCompatibleRerankPathForDashscope() {
        RagRerankProperties properties = new RagRerankProperties();
        properties.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode");
        properties.setPath("/v1/rerank");

        assertThat(AlibabaRerankService.resolvePath(properties))
                .isEqualTo("/api/v1/services/rerank/text-rerank/text-rerank");
    }

    @Test
    void buildRequestBodyDefaultsToDashscopeWhenNoProtocolOrProfileConfigured() {
        RagRerankProperties properties = new RagRerankProperties();
        properties.setModel("gte-rerank-v2");
        AlibabaRerankService service = new AlibabaRerankService(properties, new StandardEnvironment());

        Map<String, Object> body = service.buildRequestBody("query", List.of("doc1", "doc2"), 2);

        assertThat(body).containsEntry("model", "gte-rerank-v2");
        assertThat(body).containsKey("input");
        assertThat(body).containsKey("parameters");
        assertThat(body).doesNotContainKeys("query", "documents");
    }

    @Test
    void buildRequestBodyUsesExplicitVllmProtocolWhenConfigured() {
        RagRerankProperties properties = new RagRerankProperties();
        properties.setProtocol("vllm");
        properties.setModel("bge-rerank-m3-v2");
        AlibabaRerankService service = new AlibabaRerankService(properties, new StandardEnvironment());

        Map<String, Object> body = service.buildRequestBody("query", List.of("doc1", "doc2"), 2);

        assertThat(body).containsEntry("model", "bge-rerank-m3-v2");
        assertThat(body).containsEntry("query", "query");
        assertThat(body).containsEntry("documents", List.of("doc1", "doc2"));
        assertThat(body).doesNotContainKeys("input", "parameters");
    }
}
