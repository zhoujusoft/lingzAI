package lingzhou.agent.backend.business.chat.runtime;

import java.util.List;
import java.util.Locale;
import lingzhou.agent.backend.business.chat.attachment.FileParseMode;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import org.springframework.util.StringUtils;

public final class AttachmentIntentHeuristics {

    private AttachmentIntentHeuristics() {}

    public static FileParseMode resolveDefaultParseMode(List<ChatFileService.UploadedFile> files, String userMessage) {
        if (hasArchiveFile(files) || hasTabularFile(files)) {
            return FileParseMode.STRUCTURED;
        }
        if (hasReadableDocumentFile(files)) {
            if (looksLikeSchemaRequest(userMessage)) {
                return FileParseMode.STRUCTURED;
            }
            return FileParseMode.TEXT;
        }
        if (looksLikeSchemaRequest(userMessage)) {
            return FileParseMode.STRUCTURED;
        }
        return FileParseMode.STRUCTURED;
    }

    public static boolean shouldZipEscalateToCode(String userMessage) {
        String normalized = normalize(userMessage);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return containsAny(
                        normalized,
                        "提取",
                        "抽取",
                        "筛选",
                        "过滤",
                        "解压",
                        "递归",
                        "嵌套zip",
                        "嵌套 zip",
                        "只要",
                        "保留",
                        "重新打包",
                        "重打包",
                        "重新压缩",
                        "压缩一下",
                        "打包",
                        "输出zip",
                        "输出 zip")
                || (containsAny(normalized, "pdf", "发票", "文件") && containsAny(normalized, "压缩", "zip", "压缩包"));
    }

    public static boolean isReadableContentRequest(String userMessage) {
        String normalized = normalize(userMessage);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return containsAny(
                        normalized,
                        "提取内容",
                        "读取内容",
                        "读取原文",
                        "提取原文",
                        "提取这个发票内容",
                        "提取发票内容",
                        "读取发票内容",
                        "发票内容",
                        "全文",
                        "原文",
                        "摘录",
                        "摘要",
                        "总结",
                        "识别字段",
                        "识别发票",
                        "提取字段",
                        "发票号码",
                        "开票日期",
                        "价税合计",
                        "购买方",
                        "销售方",
                        "买方",
                        "卖方")
                || (containsAny(normalized, "提取", "读取", "识别", "解析")
                        && containsAny(normalized, "内容", "发票", "pdf", "文档", "字段"));
    }

    public static boolean looksLikeSchemaRequest(String userMessage) {
        String normalized = normalize(userMessage);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return containsAny(normalized, "schema", "结构", "条目结构", "表结构", "列名", "表头", "sheet", "工作表");
    }

    private static boolean hasArchiveFile(List<ChatFileService.UploadedFile> files) {
        return hasExtension(files, ".zip");
    }

    private static boolean hasTabularFile(List<ChatFileService.UploadedFile> files) {
        return hasExtension(files, ".xlsx", ".xls", ".csv", ".tsv");
    }

    private static boolean hasReadableDocumentFile(List<ChatFileService.UploadedFile> files) {
        return hasExtension(files, ".pdf", ".doc", ".docx", ".txt", ".md", ".markdown", ".html", ".htm");
    }

    private static boolean hasExtension(List<ChatFileService.UploadedFile> files, String... extensions) {
        if (files == null || files.isEmpty() || extensions == null || extensions.length == 0) {
            return false;
        }
        for (ChatFileService.UploadedFile file : files) {
            String name = file == null ? "" : normalize(file.name());
            if (!StringUtils.hasText(name)) {
                continue;
            }
            for (String extension : extensions) {
                if (StringUtils.hasText(extension) && name.endsWith(extension.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsAny(String source, String... candidates) {
        if (!StringUtils.hasText(source) || candidates == null || candidates.length == 0) {
            return false;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)
                    && source.contains(candidate.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return StringUtils.hasText(text) ? text.trim().toLowerCase(Locale.ROOT) : "";
    }
}
