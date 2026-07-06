package lingzhou.agent.backend.capability.agentruntime.v2.observation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2ObservationProjector {

    private static final Pattern HTML_TITLE_PATTERN =
            Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_HEADING_PATTERN =
            Pattern.compile("<h([1-3])[^>]*>(.*?)</h\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_CONCLUSION_PATTERN = Pattern.compile(
            "<div[^>]*class=\"[^\"]*conclusion[^\"]*\"[^>]*>.*?<p>(.*?)</p>.*?</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_ID_PATTERN = Pattern.compile("id=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public Map<String, Object> project(String toolName, Map<String, Object> arguments, Object toolResult) {
        String normalizedToolName = normalizeText(toolName);
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        Map<String, Object> payload = tryParseJsonObject(stringify(toolResult));

        Map<String, Object> toolState = new LinkedHashMap<>();
        toolState.put("toolName", normalizedToolName);
        toolState.put("logicalPath", resolveLogicalPath(normalizedToolName, safeArguments, payload));
        toolState.put("readOnly", isReadOnlyTool(normalizedToolName));

        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("toolState", Map.copyOf(toolState));

        if (payload.isEmpty()) {
            toolState.put("resultKind", "raw-text");
            projection.put("toolState", Map.copyOf(toolState));
            return projection;
        }

        toolState.put("success", readSuccessFlag(payload));
        switch (normalizedToolName.toLowerCase()) {
            case "parse_file" -> enrichParseFileProjection(projection, toolState, payload);
            case "file_read" -> enrichFileReadProjection(projection, toolState, payload);
            case "file_write" -> enrichFileWriteProjection(toolState, payload);
            case "run_python" -> enrichRunPythonProjection(toolState, payload);
            default -> toolState.put("resultKind", "json");
        }
        projection.put("toolState", Map.copyOf(toolState));
        return projection;
    }

    private void enrichParseFileProjection(
            Map<String, Object> projection, Map<String, Object> toolState, Map<String, Object> payload) {
        String fileType = normalizeText(payload.get("fileType")).toLowerCase();
        Map<String, Object> contentView = asObject(payload.get("contentView"));
        List<Object> sections = asList(contentView.get("sections"));
        List<String> sheetNames = extractSheetNames(contentView);
        boolean hasSampleRows = hasSampleRows(sections);
        boolean hasRichText = StringUtils.hasText(normalizeText(contentView.get("text")))
                || StringUtils.hasText(normalizeText(contentView.get("markdown")));
        boolean schemaOnly = Boolean.parseBoolean(String.valueOf(contentView.getOrDefault("schemaOnly", false)))
                || "schema-only".equalsIgnoreCase(normalizeText(contentView.get("contentScope")));

        toolState.put("resultKind", "parse-file");
        toolState.put("fileType", fileType);
        toolState.put("sheetNames", sheetNames);
        toolState.put("sectionCount", sections.size());

        Map<String, Object> documentState = new LinkedHashMap<>();
        documentState.put("documentKind", resolveDocumentKind(fileType));
        documentState.put("fileType", fileType);
        documentState.put("sheetNames", sheetNames);
        documentState.put("sectionCount", sections.size());
        documentState.put("hasSampleRows", hasSampleRows);
        documentState.put("hasRichText", hasRichText);
        documentState.put("schemaOnly", schemaOnly);
        projection.put("documentState", Map.copyOf(documentState));

        if ("zip".equals(fileType) && schemaOnly) {
            toolState.put("resultKind", "archive-schema");
            toolState.put("schemaOnly", Boolean.TRUE);
            return;
        }
        if (isTabularFile(fileType) && !hasSampleRows && !hasRichText) {
            toolState.put("resultKind", "tabular-schema");
            toolState.put("schemaOnly", Boolean.TRUE);
            return;
        }
    }

    private void enrichFileReadProjection(
            Map<String, Object> projection, Map<String, Object> toolState, Map<String, Object> payload) {
        Map<String, Object> data = asObject(payload.get("data"));
        String content = normalizeText(data.get("content"));
        if (!StringUtils.hasText(content)) {
            content = normalizeText(payload.get("textOutput"));
        }
        toolState.put("contentLength", content.length());
        if (looksLikeHtml(content)) {
            Map<String, Object> documentState = new LinkedHashMap<>();
            documentState.put("documentKind", "html");
            documentState.put("path", toolState.get("logicalPath"));
            documentState.put("title", extractHtmlTagContent(content, HTML_TITLE_PATTERN));
            documentState.put("headings", extractHtmlHeadings(content));
            documentState.put("conclusionPreview", extractHtmlConclusion(content));
            documentState.put("chartIds", extractHtmlElementIds(content));
            projection.put("documentState", Map.copyOf(documentState));
            toolState.put("resultKind", "html");
            return;
        }
        toolState.put("resultKind", "text");
    }

    private void enrichFileWriteProjection(Map<String, Object> toolState, Map<String, Object> payload) {
        Map<String, Object> data = asObject(payload.get("data"));
        String errorCode = normalizeText(payload.get("errorCode"));
        toolState.put("resultKind", "file-write");
        toolState.put("writtenPath", normalizeText(data.get("path")));
        if (!readSuccessFlag(payload) && "FILE_WRITE_PYTHON_BLOCKED".equalsIgnoreCase(errorCode)) {
            toolState.put("resultKind", "file-write-blocked");
            toolState.put("recoveryHint", "rewrite-python-script");
            return;
        }
    }

    private void enrichRunPythonProjection(Map<String, Object> toolState, Map<String, Object> payload) {
        Map<String, Object> data = asObject(payload.get("data"));
        toolState.put("resultKind", "python-run");
        toolState.put("scriptPath", normalizeText(data.get("scriptPath")));
    }

    private List<String> extractSheetNames(Map<String, Object> contentView) {
        Map<String, Object> entities = asObject(contentView.get("entities"));
        List<Object> sheetNames = asList(entities.get("sheetNames"));
        List<String> results = new ArrayList<>();
        for (Object item : sheetNames) {
            String text = normalizeText(item);
            if (StringUtils.hasText(text)) {
                results.add(text);
            }
        }
        return List.copyOf(results);
    }

    private boolean hasSampleRows(List<Object> sections) {
        for (Object item : sections) {
            if (!asList(asObject(item).get("sampleRows")).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String resolveLogicalPath(String toolName, Map<String, Object> arguments, Map<String, Object> payload) {
        String path = normalizeText(arguments.get("arg0"));
        if (!StringUtils.hasText(path)) {
            path = normalizeText(arguments.get("path"));
        }
        if (!StringUtils.hasText(path)) {
            path = normalizeText(asObject(payload.get("data")).get("path"));
        }
        return path.replace('\\', '/');
    }

    private boolean isReadOnlyTool(String toolName) {
        return Set.of("file_read", "parse_file", "list_dir", "stat")
                .contains(normalizeText(toolName).toLowerCase());
    }

    private boolean isTabularFile(String fileType) {
        return Set.of("xlsx", "xls", "csv", "tsv")
                .contains(normalizeText(fileType).toLowerCase());
    }

    private String resolveDocumentKind(String fileType) {
        String normalized = normalizeText(fileType).toLowerCase();
        if (isTabularFile(normalized)) {
            return "tabular";
        }
        if ("zip".equals(normalized)) {
            return "archive";
        }
        return "document";
    }

    private boolean readSuccessFlag(Map<String, Object> payload) {
        Object success = payload.get("success");
        return success != null
                && "true".equalsIgnoreCase(String.valueOf(success).trim());
    }

    private boolean looksLikeHtml(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String normalized = content.trim().toLowerCase();
        return normalized.contains("<html") || normalized.contains("<body") || normalized.contains("<!doctype html");
    }

    private String extractHtmlTagContent(String content, Pattern pattern) {
        if (!StringUtils.hasText(content) || pattern == null) {
            return "";
        }
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return "";
        }
        return stripHtmlTags(matcher.group(1));
    }

    private List<String> extractHtmlHeadings(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        Matcher matcher = HTML_HEADING_PATTERN.matcher(content);
        List<String> headings = new ArrayList<>();
        while (matcher.find() && headings.size() < 6) {
            String heading = stripHtmlTags(matcher.group(2));
            if (StringUtils.hasText(heading)) {
                headings.add(heading);
            }
        }
        return List.copyOf(headings);
    }

    private String extractHtmlConclusion(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        Matcher matcher = HTML_CONCLUSION_PATTERN.matcher(content);
        if (!matcher.find()) {
            return "";
        }
        return stripHtmlTags(matcher.group(1));
    }

    private List<String> extractHtmlElementIds(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        Matcher matcher = HTML_ID_PATTERN.matcher(content);
        List<String> ids = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        while (matcher.find() && ids.size() < 10) {
            String id = normalizeText(matcher.group(1));
            if (StringUtils.hasText(id) && seen.add(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private String stripHtmlTags(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private Map<String, Object> tryParseJsonObject(String text) {
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(text, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> asObject(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        return JSON.toJSONString(value);
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
