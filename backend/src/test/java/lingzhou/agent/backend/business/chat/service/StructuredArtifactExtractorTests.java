package lingzhou.agent.backend.business.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuredArtifactExtractorTests {

    @Test
    void extractReturnsArtifactFromJsonCodeBlock() {
        Map<String, Object> artifact = StructuredArtifactExtractor.extract(
                """
                这是结果：

                ```json
                {
                  "app": {
                    "app_name": "请假应用"
                  },
                  "meta": {
                    "active_function_code": "abc123"
                  }
                }
                ```
                """);

        assertThat(artifact).containsKeys("app", "meta");
    }

    @Test
    void extractReturnsEmptyMapForNonArtifactJson() {
        Map<String, Object> artifact = StructuredArtifactExtractor.extract(
                """
                ```json
                {
                  "name": "plain json"
                }
                ```
                """);

        assertThat(artifact).isEmpty();
    }

    @Test
    void extractAcceptsDirectJsonPayload() {
        Map<String, Object> artifact = StructuredArtifactExtractor.extract(
                """
                {
                  "meta": {
                    "active_function_code": "abc123"
                  }
                }
                """);

        assertThat(artifact).containsKey("meta");
    }
}
