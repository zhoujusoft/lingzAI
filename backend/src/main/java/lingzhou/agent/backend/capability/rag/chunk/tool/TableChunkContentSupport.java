package lingzhou.agent.backend.capability.rag.chunk.tool;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.XWPFTable;

public final class TableChunkContentSupport {

    private static final String TABLE_BLOCK_TYPE = "TABLE";
    private static final int ROOT_MARKDOWN_HEADING_LEVEL = 2;
    private static final int MAX_MARKDOWN_HEADING_LEVEL = 4;

    private TableChunkContentSupport() {}

    public static List<String> toHtmlChunks(XWPFTable table, int maxCharsPerTable) {
        int normalizedMaxChars = maxCharsPerTable > 0 ? maxCharsPerTable : Integer.MAX_VALUE / 4;
        List<String> htmlTables = WordTableProcessor.generateHtmlTables(table, normalizedMaxChars);
        List<String> chunks = new ArrayList<>(htmlTables.size());
        for (String htmlTable : htmlTables) {
            String html = StringUtils.defaultString(htmlTable).trim();
            if (html.isEmpty()) {
                html = "<table></table>";
            }
            chunks.add(html);
        }
        return chunks;
    }

    public static String formatTextContent(List<String> headings, String content) {
        return joinMarkdownBlocks(buildHeadingMarkdown(headings), StringUtils.defaultString(content).trim());
    }

    public static String formatTableContent(List<String> headings, String caption, String htmlContent) {
        List<String> blocks = new ArrayList<>();
        String headingMarkdown = buildHeadingMarkdown(headings);
        if (StringUtils.isNotBlank(headingMarkdown)) {
            blocks.add(headingMarkdown);
        }
        if (StringUtils.isNotBlank(caption)) {
            blocks.add(caption.trim());
        }
        if (StringUtils.isNotBlank(htmlContent)) {
            blocks.add(htmlContent.trim());
        }
        return joinMarkdownBlocks(blocks.toArray(String[]::new));
    }

    public static String toPlainText(String blockType, String content) {
        String source = StringUtils.defaultString(content);
        String normalized = source;
        if (isTableBlock(blockType) && source.contains("<")) {
            normalized = source.replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)</(div|p|caption)>", "\n")
                    .replaceAll("(?i)</tr>", "\n")
                    .replaceAll("(?i)</t[dh]>", " | ")
                    .replaceAll("(?i)<[^>]+>", "");
            normalized = unescapeHtml(normalized);
        }

        List<String> lines = new ArrayList<>();
        for (String line : normalized.split("\\R")) {
            String cleaned = normalizePlainTextLine(stripMarkdownSyntax(line));
            if (cleaned.isEmpty()) {
                continue;
            }
            lines.add(cleaned);
        }
        return String.join("\n", lines);
    }

    public static int resolveVisibleLength(String blockType, String content) {
        return toPlainText(blockType, content).length();
    }

    public static boolean isTableBlock(String blockType) {
        return TABLE_BLOCK_TYPE.equalsIgnoreCase(StringUtils.defaultString(blockType).trim());
    }

    private static String buildHeadingMarkdown(List<String> headings) {
        if (headings == null || headings.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < headings.size(); i++) {
            String heading = headings.get(i);
            if (StringUtils.isBlank(heading)) {
                continue;
            }
            int level = Math.min(ROOT_MARKDOWN_HEADING_LEVEL + i, MAX_MARKDOWN_HEADING_LEVEL);
            lines.add("#".repeat(level) + " " + heading.trim());
        }
        return String.join("\n", lines);
    }

    private static String joinMarkdownBlocks(String... blocks) {
        List<String> normalizedBlocks = new ArrayList<>();
        for (String block : blocks) {
            if (StringUtils.isBlank(block)) {
                continue;
            }
            normalizedBlocks.add(block.trim());
        }
        return String.join("\n\n", normalizedBlocks);
    }

    private static String normalizePlainTextLine(String line) {
        String normalized = StringUtils.defaultString(line)
                .replace('\u00A0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\s*\\|\\s*", " | ")
                .replaceAll("^(\\|\\s*)+", "")
                .replaceAll("(\\s*\\|)+$", "")
                .trim();
        return normalized;
    }

    private static String stripMarkdownSyntax(String line) {
        return StringUtils.defaultString(line)
                .replaceAll("^\\s{0,3}#{1,6}\\s+", "")
                .replaceAll("^\\s*>\\s+", "")
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("__(.*?)__", "$1")
                .trim();
    }

    private static String unescapeHtml(String content) {
        return StringUtils.defaultString(content)
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }
}
