package lingzhou.agent.backend.capability.webfetch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.junit.jupiter.api.Test;

class WebFetchToolProviderTest {

    @Test
    void shouldSerializeRecordResultAsJsonObject() {
        WebFetchService service = new WebFetchService() {
            @Override
            public WebFetchResult fetch(WebFetchRequest request) {
                return new WebFetchResult(
                        true,
                        "",
                        request.url(),
                        200,
                        "text/html",
                        "测试标题",
                        "测试摘要",
                        "测试公众号",
                        "作者",
                        "1781143209",
                        "https://example.com/cover.png",
                        "# 测试标题\n\n正文",
                        "正文",
                        false,
                        true,
                        false,
                        java.util.List.of("https://example.com/a.png"),
                        java.util.List.of(),
                        java.util.Map.of("mid", "1"),
                        "");
            }
        };
        WebFetchToolProvider provider = new WebFetchToolProvider(service, new ObjectMapper());

        String json = provider.webFetch("https://example.com", null, null, true, false);

        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"title\":\"测试标题\"");
        assertThat(json).contains("\"content\":\"正文\"");
        assertThat(json).contains("\"metadata\":{\"mid\":\"1\"}");
    }

    @Test
    void shouldSerializeErrorResult() {
        WebFetchService service = new WebFetchService() {
            @Override
            public WebFetchResult fetch(WebFetchRequest request) throws TaskException {
                throw new WebFetchException("INVALID_URL", "url 不能为空");
            }
        };
        WebFetchToolProvider provider = new WebFetchToolProvider(service, new ObjectMapper());

        String json = provider.webFetch("", null, null, true, false);

        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"errorCode\":\"INVALID_URL\"");
        assertThat(json).contains("\"errorMessage\":\"url 不能为空\"");
    }
}
