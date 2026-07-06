package lingzhou.agent.backend.capability.webfetch;

import java.io.IOException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WebFetchService {

    private static final int DEFAULT_MAX_CHARS = 30_000;
    private static final int MAX_CHARS_LIMIT = 100_000;
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGES = 80;
    private static final int MAX_LINKS = 120;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_MARKDOWN_CHARS = 120_000;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private final HttpClient httpClient;

    public WebFetchService() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    WebFetchService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public WebFetchResult fetch(WebFetchRequest request) throws TaskException {
        URI uri = validateUrl(
                request == null ? null : request.url(), request == null ? null : request.allowedDomains());
        int maxChars = normalizeMaxChars(request == null ? null : request.maxChars());
        HttpResponse<byte[]> response = null;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            response = send(uri);
            if (!isRedirect(response.statusCode())) {
                break;
            }
            String location = response.headers().firstValue("location").orElse("");
            if (!StringUtils.hasText(location)) {
                break;
            }
            uri = validateUrl(uri.resolve(location).toString(), request == null ? null : request.allowedDomains());
            if (redirects == MAX_REDIRECTS) {
                throw new TaskException("网页重定向次数过多", TaskException.Code.UNKNOWN);
            }
        }
        if (response == null) {
            throw new TaskException("网页请求未返回响应", TaskException.Code.UNKNOWN);
        }

        byte[] body = response.body() == null ? new byte[0] : response.body();
        if (body.length > MAX_RESPONSE_BYTES) {
            throw new WebFetchException("CONTENT_TOO_LARGE", "网页响应过大，已超过 " + MAX_RESPONSE_BYTES + " bytes");
        }
        String contentType = response.headers().firstValue("content-type").orElse("");
        if (StringUtils.hasText(contentType) && !contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
            throw new WebFetchException("NOT_HTML", "仅支持 HTML 网页，当前 content-type=" + contentType);
        }
        String html = new String(body, StandardCharsets.UTF_8);
        return parse(uri.toString(), response.statusCode(), contentType, html, request, maxChars);
    }

    private HttpResponse<byte[]> send(URI uri) throws TaskException {
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .GET()
                .build();
        try {
            return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException ex) {
            throw new TaskException("网页请求失败：" + ex.getMessage(), TaskException.Code.UNKNOWN, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TaskException("网页请求被中断", TaskException.Code.UNKNOWN, ex);
        }
    }

    WebFetchResult parse(
            String url, int statusCode, String contentType, String html, WebFetchRequest request, int maxChars) {
        Document document = Jsoup.parse(html == null ? "" : html, url);
        boolean verificationPage = isVerificationPage(document);
        Element contentElement = selectContent(document, request == null ? null : request.selector());
        boolean wechatArticle = document.selectFirst("#js_content") != null;
        String content = contentElement == null ? "" : cleanText(contentElement);
        if (!StringUtils.hasText(content)) {
            content = document.body() == null ? "" : cleanText(document.body());
        }
        TruncatedText truncated = truncate(content, maxChars);
        List<String> images = Boolean.FALSE.equals(request == null ? null : request.includeImages())
                ? List.of()
                : extractImages(contentElement == null ? document : contentElement);
        List<WebFetchResult.Link> links = Boolean.TRUE.equals(request == null ? null : request.includeLinks())
                ? extractLinks(contentElement == null ? document : contentElement)
                : List.of();
        Map<String, String> metadata = extractMetadata(document, html);
        String markdown = toMarkdown(contentElement == null ? document.body() : contentElement);
        TruncatedText truncatedMarkdown = truncate(markdown, Math.min(MAX_MARKDOWN_CHARS, maxChars * 2));
        String errorCode = verificationPage ? "WECHAT_VERIFICATION" : "";

        return new WebFetchResult(
                statusCode >= 200 && statusCode < 400 && !verificationPage,
                errorCode,
                url,
                statusCode,
                contentType == null ? "" : contentType,
                firstText(document, "#activity-name", titleFromDocument(document)),
                firstNonBlank(metadata.get("msg_desc"), metaContent(document, "description")),
                firstText(document, "#js_name", ""),
                firstNonBlank(firstText(document, "#js_author_name", ""), metadata.get("author")),
                firstNonBlank(metadata.get("create_time"), metadata.get("oriCreateTime"), metadata.get("ct")),
                firstNonBlank(metadata.get("msg_cdn_url"), metaContent(document, "og:image")),
                truncatedMarkdown.value(),
                truncated.value(),
                truncated.truncated(),
                wechatArticle,
                verificationPage,
                images,
                links,
                metadata,
                verificationPage ? "页面疑似为验证或环境异常页" : "");
    }

    private Element selectContent(Document document, String selector) {
        if (StringUtils.hasText(selector)) {
            Element selected = document.selectFirst(selector.trim());
            if (selected != null) {
                return selected;
            }
        }
        for (String candidate : List.of("#js_content", "article", "main", ".article", ".content", "#content")) {
            Element selected = document.selectFirst(candidate);
            if (selected != null) {
                return selected;
            }
        }
        Element scored = document.select("article,main,section,div").stream()
                .max(Comparator.comparingDouble(this::contentScore))
                .orElse(null);
        return scored == null || contentScore(scored) <= 0 ? document.body() : scored;
    }

    private String cleanText(Element element) {
        Element copy = element.clone();
        copy.select("script,style,noscript,iframe,svg").remove();
        String text = copy.text();
        return text == null ? "" : text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private double contentScore(Element element) {
        String text = cleanText(element);
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int textLength = text.length();
        int paragraphCount = element.select("p").size();
        int headingCount = element.select("h1,h2,h3,h4").size();
        int linkTextLength = element.select("a").stream()
                .map(Element::text)
                .mapToInt(value -> value == null ? 0 : value.length())
                .sum();
        double linkDensity = textLength == 0 ? 0 : (double) linkTextLength / textLength;
        double penalty = linkDensity > 0.35 ? textLength * linkDensity : 0;
        return textLength + paragraphCount * 80.0 + headingCount * 40.0 - penalty;
    }

    private List<String> extractImages(Element root) {
        Set<String> images = new LinkedHashSet<>();
        for (Element image : root.select("img")) {
            String src = firstNonBlank(
                    image.attr("abs:data-src"), image.attr("abs:src"), image.attr("data-src"), image.attr("src"));
            if (StringUtils.hasText(src)) {
                images.add(src);
            }
            if (images.size() >= MAX_IMAGES) {
                break;
            }
        }
        return List.copyOf(images);
    }

    private List<WebFetchResult.Link> extractLinks(Element root) {
        List<WebFetchResult.Link> links = new ArrayList<>();
        for (Element link : root.select("a[href]")) {
            String href = link.attr("abs:href");
            String text = link.text();
            if (StringUtils.hasText(href)) {
                links.add(new WebFetchResult.Link(text == null ? "" : text.trim(), href));
            }
            if (links.size() >= MAX_LINKS) {
                break;
            }
        }
        return List.copyOf(links);
    }

    private URI validateUrl(String rawUrl, List<String> allowedDomains) throws TaskException {
        if (!StringUtils.hasText(rawUrl)) {
            throw new WebFetchException("INVALID_URL", "url 不能为空");
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException ex) {
            throw new WebFetchException("INVALID_URL", "url 格式无效", ex);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new WebFetchException("INVALID_SCHEME", "仅支持 http/https URL");
        }
        String host = normalizeHost(uri.getHost());
        if (!StringUtils.hasText(host)) {
            throw new WebFetchException("INVALID_URL", "url host 不能为空");
        }
        if (!matchesAllowedDomains(host, allowedDomains)) {
            throw new WebFetchException("DOMAIN_NOT_ALLOWED", "url 不在允许访问的域名范围内");
        }
        assertPublicHost(host);
        return uri;
    }

    private boolean matchesAllowedDomains(String host, List<String> allowedDomains) {
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            return true;
        }
        for (String allowedDomain : allowedDomains) {
            String normalized = normalizeHost(allowedDomain);
            if (StringUtils.hasText(normalized) && (host.equals(normalized) || host.endsWith("." + normalized))) {
                return true;
            }
        }
        return false;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private void assertPublicHost(String host) throws TaskException {
        if ("localhost".equals(host) || host.endsWith(".localhost")) {
            throw new WebFetchException("PRIVATE_ADDRESS_BLOCKED", "禁止访问 localhost");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (IOException ex) {
            throw new WebFetchException("DNS_FAILED", "解析域名失败：" + host, ex);
        }
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new WebFetchException("PRIVATE_ADDRESS_BLOCKED", "禁止访问内网或本机地址：" + host);
            }
        }
    }

    private String normalizeHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "";
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            try {
                normalized = new URI(normalized).getHost();
            } catch (URISyntaxException ignored) {
                return "";
            }
        }
        return StringUtils.hasText(normalized) ? IDN.toASCII(normalized) : "";
    }

    private int normalizeMaxChars(Integer maxChars) {
        if (maxChars == null || maxChars <= 0) {
            return DEFAULT_MAX_CHARS;
        }
        return Math.min(maxChars, MAX_CHARS_LIMIT);
    }

    private boolean isVerificationPage(Document document) {
        String text = document.text();
        return text.contains("环境异常") || text.contains("完成验证") || text.contains("访问过于频繁") || text.contains("安全验证");
    }

    private String firstText(Document document, String selector, String fallback) {
        Element element = document.selectFirst(selector);
        String value = element == null ? "" : element.text().trim();
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String titleFromDocument(Document document) {
        return document.title() == null ? "" : document.title().trim();
    }

    private Map<String, String> extractMetadata(Document document, String html) {
        Map<String, String> metadata = new LinkedHashMap<>();
        putIfText(metadata, "msg_title", extractScriptString(html, "msg_title"));
        putIfText(metadata, "msg_desc", extractScriptString(html, "msg_desc"));
        putIfText(metadata, "msg_cdn_url", extractScriptString(html, "msg_cdn_url"));
        putIfText(metadata, "nickname", extractScriptString(html, "nickname"));
        putIfText(metadata, "author", extractScriptString(html, "author"));
        putIfText(metadata, "biz", extractScriptString(html, "__biz"));
        putIfText(metadata, "mid", extractScriptString(html, "mid"));
        putIfText(metadata, "idx", extractScriptString(html, "idx"));
        putIfText(metadata, "ct", extractScriptString(html, "ct"));
        putIfText(metadata, "create_time", extractScriptString(html, "create_time"));
        putIfText(metadata, "oriCreateTime", extractScriptString(html, "oriCreateTime"));
        putIfText(metadata, "description", metaContent(document, "description"));
        putIfText(metadata, "og:image", metaContent(document, "og:image"));
        return Map.copyOf(metadata);
    }

    private void putIfText(Map<String, String> metadata, String key, String value) {
        if (StringUtils.hasText(value)) {
            metadata.put(key, value.trim());
        }
    }

    private String metaContent(Document document, String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        Element element = document.selectFirst("meta[name=" + name + "],meta[property=" + name + "]");
        return element == null ? "" : element.attr("content").trim();
    }

    private String extractScriptString(String html, String name) {
        if (!StringUtils.hasText(html) || !StringUtils.hasText(name)) {
            return "";
        }
        Pattern pattern = Pattern.compile(
                "\\b(?:var\\s+)?" + Pattern.quote(name) + "\\s*=\\s*(.*?);",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            return "";
        }
        return extractBestStringLiteral(matcher.group(1));
    }

    private String extractBestStringLiteral(String expression) {
        if (!StringUtils.hasText(expression)) {
            return "";
        }
        Pattern quotedLiteral = Pattern.compile("([\"'])(.*?)\\1", Pattern.DOTALL);
        Matcher matcher = quotedLiteral.matcher(expression);
        String best = "";
        while (matcher.find()) {
            String value = unescapeScriptString(matcher.group(2));
            if (StringUtils.hasText(value) && value.length() > best.length()) {
                best = value;
            }
        }
        return best.trim();
    }

    private String unescapeScriptString(String value) {
        return value == null
                ? ""
                : value.replace("\\\"", "\"")
                        .replace("\\'", "'")
                        .replace("\\/", "/")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t");
    }

    private String toMarkdown(Element root) {
        if (root == null) {
            return "";
        }
        StringBuilder markdown = new StringBuilder();
        appendMarkdown(root.clone(), markdown, 0);
        return markdown.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    private void appendMarkdown(Element element, StringBuilder markdown, int listDepth) {
        element.select("script,style,noscript,iframe,svg").remove();
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                int level = Math.min(6, Math.max(1, Integer.parseInt(tag.substring(1))));
                appendBlock(markdown, "#".repeat(level) + " " + element.text().trim());
            }
            case "p" -> appendBlock(markdown, element.text().trim());
            case "blockquote" -> appendBlock(markdown, "> " + element.text().trim());
            case "pre" -> appendBlock(markdown, "```\n" + element.text().trim() + "\n```");
            case "code" -> markdown.append('`').append(element.text().trim()).append('`');
            case "li" -> appendBlock(markdown, "  ".repeat(Math.max(0, listDepth)) + "- " + element.text().trim());
            case "img" -> {
                String src = firstNonBlank(
                        element.attr("abs:data-src"), element.attr("abs:src"), element.attr("data-src"), element.attr("src"));
                if (StringUtils.hasText(src)) {
                    appendBlock(markdown, "![](" + src + ")");
                }
            }
            case "table" -> appendBlock(markdown, tableToMarkdown(element));
            case "ul", "ol" -> {
                for (Element child : element.children()) {
                    appendMarkdown(child, markdown, listDepth + 1);
                }
            }
            default -> {
                for (Element child : element.children()) {
                    appendMarkdown(child, markdown, listDepth);
                }
            }
        }
    }

    private void appendBlock(StringBuilder markdown, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!markdown.isEmpty() && markdown.charAt(markdown.length() - 1) != '\n') {
            markdown.append('\n');
        }
        markdown.append(value.trim()).append("\n\n");
    }

    private String tableToMarkdown(Element table) {
        List<Element> rows = table.select("tr");
        if (rows.isEmpty()) {
            return "";
        }
        List<List<String>> cells = rows.stream()
                .map(row -> row.select("th,td").stream()
                        .map(cell -> cell.text().trim().replace("|", "\\|"))
                        .toList())
                .filter(row -> !row.isEmpty())
                .toList();
        if (cells.isEmpty()) {
            return "";
        }
        int columns = cells.stream().mapToInt(List::size).max().orElse(0);
        StringBuilder tableMarkdown = new StringBuilder();
        appendTableRow(tableMarkdown, cells.get(0), columns);
        appendTableSeparator(tableMarkdown, columns);
        for (int i = 1; i < cells.size(); i++) {
            appendTableRow(tableMarkdown, cells.get(i), columns);
        }
        return tableMarkdown.toString().trim();
    }

    private void appendTableRow(StringBuilder builder, List<String> row, int columns) {
        builder.append('|');
        for (int i = 0; i < columns; i++) {
            builder.append(' ').append(i < row.size() ? row.get(i) : "").append(" |");
        }
        builder.append('\n');
    }

    private void appendTableSeparator(StringBuilder builder, int columns) {
        builder.append('|');
        for (int i = 0; i < columns; i++) {
            builder.append(" --- |");
        }
        builder.append('\n');
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private TruncatedText truncate(String value, int maxChars) {
        String normalized = value == null ? "" : value;
        if (normalized.length() <= maxChars) {
            return new TruncatedText(normalized, false);
        }
        return new TruncatedText(normalized.substring(0, maxChars), true);
    }

    private record TruncatedText(String value, boolean truncated) {}

    static class WebFetchException extends TaskException {

        private final String errorCode;

        WebFetchException(String errorCode, String msg) {
            super(msg, Code.UNKNOWN);
            this.errorCode = errorCode;
        }

        WebFetchException(String errorCode, String msg, Exception nestedEx) {
            super(msg, Code.UNKNOWN, nestedEx);
            this.errorCode = errorCode;
        }

        String getErrorCode() {
            return errorCode;
        }
    }
}
