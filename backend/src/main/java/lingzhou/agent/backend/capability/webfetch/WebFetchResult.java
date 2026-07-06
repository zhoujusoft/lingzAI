package lingzhou.agent.backend.capability.webfetch;

import java.util.List;
import java.util.Map;

public record WebFetchResult(
        boolean success,
        String errorCode,
        String url,
        int statusCode,
        String contentType,
        String title,
        String description,
        String accountName,
        String author,
        String publishTime,
        String coverImage,
        String markdown,
        String content,
        boolean truncated,
        boolean wechatArticle,
        boolean verificationPage,
        List<String> images,
        List<Link> links,
        Map<String, String> metadata,
        String errorMessage) {

    public record Link(String text, String url) {}
}
