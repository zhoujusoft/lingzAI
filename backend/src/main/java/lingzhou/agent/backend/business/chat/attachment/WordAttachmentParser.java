package lingzhou.agent.backend.business.chat.attachment;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lingzhou.agent.backend.capability.rag.chunk.model.FileType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

@Component
public class WordAttachmentParser implements AttachmentParser {

    private static final int MAX_SECTION_COUNT = 24;
    private static final int MAX_TEXT_LENGTH = 300;
    private static final int MAX_SAMPLE_ROWS = 3;
    private static final int MAX_ENTITY_COUNT = 24;
    private static final Pattern HEADING_PUNCTUATION = Pattern.compile("[：:,，。；;]");

    @Override
    public boolean supports(String fileName) {
        return extension(fileName).equalsIgnoreCase(FileType.DOCX);
    }

    @Override
    public AttachmentParseResult parse(InputStream inputStream, String fileName) throws Exception {
        List<AttachmentParseResult.Section> sections = new ArrayList<>();
        List<AttachmentParseResult.EntityTable> tables = new ArrayList<>();
        Set<String> headings = new LinkedHashSet<>();
        Set<String> labels = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        int paragraphCount = 0;
        int tableCount = 0;
        int sectionIndex = 0;

        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            for (IBodyElement element : document.getBodyElements()) {
                if (sectionIndex >= MAX_SECTION_COUNT) {
                    warnings.add("文档内容较长，预解析结果已截断");
                    break;
                }
                if (element instanceof XWPFParagraph paragraph) {
                    String text = normalizeText(paragraph.getText());
                    if (text.isEmpty()) {
                        continue;
                    }
                    paragraphCount++;
                    sections.add(new AttachmentParseResult.Section(
                            "paragraph",
                            sectionIndex++,
                            "",
                            truncate(text, MAX_TEXT_LENGTH),
                            null,
                            null,
                            null,
                            List.of(),
                            List.of(),
                            List.of()));
                    if (looksLikeHeading(text) && headings.size() < MAX_ENTITY_COUNT) {
                        headings.add(text);
                    }
                    collectLabels(labels, text);
                    continue;
                }
                if (element instanceof XWPFTable table) {
                    List<List<String>> rows = toRows(table);
                    if (rows.isEmpty()) {
                        continue;
                    }
                    tableCount++;
                    List<String> header = rows.get(0);
                    List<List<String>> sampleRows = limitRows(rows.subList(Math.min(1, rows.size()), rows.size()), MAX_SAMPLE_ROWS);
                    sections.add(new AttachmentParseResult.Section(
                            "table",
                            sectionIndex++,
                            "",
                            "",
                            rows.size(),
                            maxColumnCount(rows),
                            null,
                            header,
                            sampleRows,
                            List.of()));
                    tables.add(new AttachmentParseResult.EntityTable("", header, sampleRows));
                    for (List<String> row : rows) {
                        for (String cell : row) {
                            collectLabels(labels, cell);
                        }
                    }
                }
            }
        }

        return new AttachmentParseResult(
                true,
                fileName,
                FileType.DOCX,
                new AttachmentParseResult.Summary(paragraphCount, tableCount, 0, sections.size()),
                sections,
                new AttachmentParseResult.Entities(
                        limitStrings(headings, MAX_ENTITY_COUNT),
                        limitStrings(labels, MAX_ENTITY_COUNT),
                        tables,
                        List.of()),
                warnings,
                "");
    }

    private List<List<String>> toRows(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> values = new ArrayList<>();
            boolean hasValue = false;
            for (XWPFTableCell cell : row.getTableCells()) {
                String value = normalizeText(cell == null ? "" : cell.getText());
                values.add(value);
                hasValue = hasValue || !value.isEmpty();
            }
            if (hasValue) {
                rows.add(values);
            }
        }
        return rows;
    }

    private void collectLabels(Set<String> labels, String text) {
        if (text == null || text.isBlank() || labels.size() >= MAX_ENTITY_COUNT) {
            return;
        }
        for (String separator : List.of("：", ":", "、", "，", ",")) {
            if (!text.contains(separator)) {
                continue;
            }
            for (String part : text.split(Pattern.quote(separator))) {
                String normalized = normalizeText(part);
                if (normalized.length() >= 1 && normalized.length() <= 20 && labels.size() < MAX_ENTITY_COUNT) {
                    labels.add(normalized);
                }
            }
        }
    }

    private boolean looksLikeHeading(String text) {
        return text.length() <= 20 && !HEADING_PUNCTUATION.matcher(text).find();
    }

    private List<String> limitStrings(Set<String> values, int maxCount) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (result.size() >= maxCount) {
                break;
            }
            result.add(truncate(value, 80));
        }
        return result;
    }

    private List<List<String>> limitRows(List<List<String>> rows, int maxCount) {
        List<List<String>> result = new ArrayList<>();
        for (List<String> row : rows) {
            if (result.size() >= maxCount) {
                break;
            }
            result.add(row.stream().map(value -> truncate(value, 120)).toList());
        }
        return result;
    }

    private int maxColumnCount(List<List<String>> rows) {
        int max = 0;
        for (List<String> row : rows) {
            max = Math.max(max, row.size());
        }
        return max;
    }

    private String extension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).trim().toLowerCase();
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
