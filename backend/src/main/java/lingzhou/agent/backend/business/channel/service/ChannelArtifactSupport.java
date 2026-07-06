package lingzhou.agent.backend.business.channel.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lingzhou.agent.backend.business.channel.model.ChannelDispatchResult;
import lingzhou.agent.backend.business.channel.model.ChannelMessage;
import org.springframework.util.StringUtils;

final class ChannelArtifactSupport {

    private static final Pattern HTTP_URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private ChannelArtifactSupport() {}

    static boolean requiresArtifactOutput(ChannelMessage message) {
        if (message == null) {
            return false;
        }
        String text = normalizeText(message.getContent());
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return hasOutputAction(lower) && hasFileTarget(lower);
    }

    static boolean isStandaloneFileMessage(ChannelMessage message) {
        if (message == null || hasTextPart(message)) {
            return false;
        }
        String inputMode = normalizeText(message.getInputMode()).toLowerCase(Locale.ROOT);
        String contentType = normalizeText(message.getContentType()).toLowerCase(Locale.ROOT);
        if ("file".equals(inputMode) || "file".equals(contentType)) {
            return true;
        }
        String content = normalizeText(message.getContent());
        return content.startsWith("[文件]") || content.startsWith("[文件消息]");
    }

    static boolean requiresReadableFileButMissing(ChannelMessage message) {
        if (message == null) {
            return false;
        }
        if (!resolveFileIds(message).isEmpty()) {
            return false;
        }
        String lower = normalizeText(message.getContent()).toLowerCase(Locale.ROOT);
        return containsAny(
                lower,
                "刚才上传",
                "上传的文件",
                "上传的附件",
                "读取文件",
                "读取这个",
                "读取此",
                "读取刚才",
                "文件内容",
                "附件内容",
                "表格内容",
                "excel",
                "xlsx",
                "xls",
                "提取",
                "读取",
                "解析",
                "抽取");
    }

    static List<String> resolveFileIds(ChannelMessage message) {
        if (message == null) {
            return List.of();
        }
        Set<String> fileIds = new LinkedHashSet<>();
        addFileIds(fileIds, message.getFileIds());
        Map<String, Object> metadata = message.getMetadata();
        if (metadata != null && !metadata.isEmpty()) {
            addFileIds(fileIds, metadata.get("fileIds"));
            addFileIds(fileIds, metadata.get("fileId"));
            Object channelFileContext = metadata.get("channelFileContext");
            if (channelFileContext instanceof Map<?, ?> context) {
                addFileIds(fileIds, context.get("fileIds"));
                addFileIds(fileIds, context.get("fileId"));
            }
            Object pendingChannelFileContext = metadata.get("pendingChannelFileContext");
            if (pendingChannelFileContext instanceof Map<?, ?> context) {
                addFileIds(fileIds, context.get("fileIds"));
                addFileIds(fileIds, context.get("fileId"));
            }
            Object parts = metadata.get("parts");
            if (parts instanceof List<?> partList) {
                for (Object item : partList) {
                    if (item instanceof Map<?, ?> part) {
                        addFileIds(fileIds, part.get("fileIds"));
                        addFileIds(fileIds, part.get("fileId"));
                        addFileIds(fileIds, part.get("id"));
                    }
                }
            }
        }
        return List.copyOf(fileIds);
    }

    static List<String> resolveMediaSummaries(ChannelMessage message) {
        if (message == null || message.getMetadata() == null || message.getMetadata().isEmpty()) {
            return List.of();
        }
        Object mediaSummaries = message.getMetadata().get("mediaSummaries");
        if (!(mediaSummaries instanceof List<?> summaries)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object summary : summaries) {
            if (summary != null && StringUtils.hasText(String.valueOf(summary))) {
                result.add(String.valueOf(summary).trim());
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    static Map<String, Object> buildRuntimeOptions(List<String> fileIds, boolean artifactRequired) {
        Map<String, Object> options = new LinkedHashMap<>();
        if (fileIds != null && !fileIds.isEmpty()) {
            options.put("parseAttachments", "structured");
        }
        if (artifactRequired) {
            options.put("artifactRequired", Boolean.TRUE);
            options.put("channelArtifactRequired", Boolean.TRUE);
        }
        return options.isEmpty() ? null : options;
    }

    static List<ChannelDispatchResult.GeneratedFile> extractGeneratedFiles(Object value) {
        List<ChannelDispatchResult.GeneratedFile> files = new ArrayList<>();
        collectGeneratedFiles(value, files, new LinkedHashSet<>());
        return files.isEmpty() ? List.of() : List.copyOf(files);
    }

    static String stripDownloadUrls(String reply) {
        if (!StringUtils.hasText(reply)) {
            return "";
        }
        String stripped = reply.replaceAll("\\[([^\\]]+)]\\(https?://[^\\s)]+\\)", "$1");
        stripped = HTTP_URL_PATTERN.matcher(stripped).replaceAll("");
        stripped = stripped.replaceAll("(?im)^\\s*download\\s*[:：].*$", "");
        stripped = stripped.replaceAll("(?im)^.*downloadUrl\\s*[:：].*$", "");
        stripped = stripped.replaceAll("(?m)[ \\t]+$", "");
        stripped = stripped.replaceAll("\\n{3,}", "\n\n");
        return stripped.trim();
    }

    private static boolean hasOutputAction(String lower) {
        return containsAny(
                lower,
                "生成",
                "导出",
                "保存",
                "另存",
                "下载",
                "输出为",
                "输出成",
                "返回文件",
                "整理成",
                "转换成",
                "转成",
                "写成",
                "写入",
                "做成",
                "产出",
                "创建");
    }

    private static boolean hasFileTarget(String lower) {
        return containsAny(
                lower,
                "文件",
                "文档",
                ".txt",
                "txt",
                ".md",
                "md文档",
                "md 文档",
                "md格式",
                "md 格式",
                "markdown",
                ".csv",
                "csv",
                ".json",
                "json",
                ".xlsx",
                ".xls",
                "excel",
                ".docx",
                "word",
                ".pdf",
                "pdf",
                ".html",
                "html");
    }

    private static boolean containsAny(String source, String... candidates) {
        if (!StringUtils.hasText(source)) {
            return false;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate) && source.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTextPart(ChannelMessage message) {
        Map<String, Object> metadata = message == null ? null : message.getMetadata();
        Object parts = metadata == null ? null : metadata.get("parts");
        if (!(parts instanceof List<?> partList)) {
            return false;
        }
        for (Object item : partList) {
            if (item instanceof Map<?, ?> part
                    && "text".equalsIgnoreCase(String.valueOf(part.get("type")))
                    && StringUtils.hasText(firstText(text(part.get("content")), text(part.get("summary"))))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPendingFileContext(ChannelMessage message) {
        Map<String, Object> metadata = message == null ? null : message.getMetadata();
        return metadata != null && metadata.containsKey("pendingChannelFileContext");
    }

    private static void addFileIds(Set<String> fileIds, Object value) {
        if (fileIds == null || value == null) {
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                addFileIds(fileIds, item);
            }
            return;
        }
        if (value instanceof String text) {
            for (String part : text.split(",")) {
                String normalized = part.trim();
                if (StringUtils.hasText(normalized)) {
                    fileIds.add(normalized);
                }
            }
            return;
        }
        String normalized = String.valueOf(value).trim();
        if (StringUtils.hasText(normalized)) {
            fileIds.add(normalized);
        }
    }

    private static void collectGeneratedFiles(
            Object value, List<ChannelDispatchResult.GeneratedFile> files, Set<String> seenKeys) {
        if (value == null) {
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                collectGeneratedFiles(item, files, seenKeys);
            }
            return;
        }
        if (value instanceof String text) {
            collectGeneratedFiles(parseJsonLike(text), files, seenKeys);
            return;
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            return;
        }
        Map<String, Object> map = normalizeMap(rawMap);
        ChannelDispatchResult.GeneratedFile file = toGeneratedFile(map);
        if (file != null) {
            String key = firstText(file.downloadUrl(), file.objectName(), file.assetCode(), file.id(), file.fileName());
            if (seenKeys.add(key)) {
                files.add(file);
            }
        }
        collectGeneratedFiles(map.get("artifact"), files, seenKeys);
        collectGeneratedFiles(map.get("artifacts"), files, seenKeys);
        collectGeneratedFiles(map.get("file"), files, seenKeys);
        collectGeneratedFiles(map.get("files"), files, seenKeys);
        collectGeneratedFiles(map.get("data"), files, seenKeys);
        collectGeneratedFiles(map.get("content"), files, seenKeys);
        collectGeneratedFiles(map.get("response"), files, seenKeys);
        collectGeneratedFiles(map.get("textOutput"), files, seenKeys);
    }

    private static Object parseJsonLike(String text) {
        String normalized = normalizeText(text);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            if (normalized.startsWith("{")) {
                return JSON.parseObject(normalized, new TypeReference<Map<String, Object>>() {});
            }
            if (normalized.startsWith("[")) {
                return JSON.parseArray(normalized, Object.class);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static ChannelDispatchResult.GeneratedFile toGeneratedFile(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String downloadUrl = text(map.get("downloadUrl"));
        String objectName = text(map.get("objectName"));
        String assetCode = text(map.get("assetCode"));
        String id = firstText(text(map.get("id")), text(map.get("fileId")));
        String fileName = firstText(text(map.get("fileName")), text(map.get("name")));
        if (!StringUtils.hasText(downloadUrl)) {
            return null;
        }
        return new ChannelDispatchResult.GeneratedFile(
                id,
                fileName,
                downloadUrl,
                text(map.get("previewUrl")),
                text(map.get("contentType")),
                objectName,
                assetCode);
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
