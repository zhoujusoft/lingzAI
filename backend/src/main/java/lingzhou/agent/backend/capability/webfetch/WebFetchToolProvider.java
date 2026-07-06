package lingzhou.agent.backend.capability.webfetch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WebFetchToolProvider {

    private final WebFetchService webFetchService;
    private final ObjectMapper objectMapper;

    public WebFetchToolProvider(WebFetchService webFetchService, ObjectMapper objectMapper) {
        this.webFetchService = webFetchService;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = "web_fetch",
            description =
                    """
                    Fetch a public HTML webpage and extract readable content. Use this for static public articles, docs, notices and WeChat public account article URLs. The tool returns JSON with title, content, images, links and flags such as wechatArticle/verificationPage. It does not execute JavaScript or bypass login, captcha or access verification.
                    """)
    public String webFetch(
            @ToolParam(description = "Public http/https page URL") String url,
            @ToolParam(description = "Optional CSS selector for the main content container, e.g. #js_content")
                    String selector,
            @ToolParam(description = "Optional max content characters, default 30000, max 100000") Integer maxChars,
            @ToolParam(description = "Whether to include image URLs, default true") Boolean includeImages,
            @ToolParam(description = "Whether to include links, default false") Boolean includeLinks) {
        try {
            return toJson(webFetchService.fetch(new WebFetchRequest(url, selector, maxChars, includeImages, includeLinks, null)));
        } catch (TaskException ex) {
            String errorCode = ex instanceof WebFetchService.WebFetchException webFetchException
                    ? webFetchException.getErrorCode()
                    : "FETCH_FAILED";
            return toJson(new WebFetchResult(
                    false,
                    errorCode,
                    url,
                    0,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    false,
                    false,
                    false,
                    java.util.List.<String>of(),
                    java.util.List.<WebFetchResult.Link>of(),
                    java.util.Map.<String, String>of(),
                    ex.getMessage()));
        }
    }

    private String toJson(WebFetchResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "{\"success\":false,\"errorCode\":\"SERIALIZE_FAILED\",\"errorMessage\":\"网页抓取结果序列化失败\"}";
        }
    }
}
