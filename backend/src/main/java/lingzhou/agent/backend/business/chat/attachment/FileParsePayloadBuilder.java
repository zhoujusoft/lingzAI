package lingzhou.agent.backend.business.chat.attachment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class FileParsePayloadBuilder {

    private static final int MAX_SECTION_COUNT = 12;
    private static final int MAX_HEADER_COUNT = 20;
    private static final int MAX_SAMPLE_ROW_COUNT = 6;
    private static final int MAX_SAMPLE_ROW_COLUMN_COUNT = 12;
    private static final int MAX_COLUMN_COUNT = 20;
    private static final int MAX_COLUMN_SAMPLE_VALUE_COUNT = 3;
    private static final int MAX_HEADING_COUNT = 12;
    private static final int MAX_LABEL_COUNT = 12;
    private static final int MAX_SHEET_NAME_COUNT = 12;
    private static final int MAX_ENTITY_TABLE_COUNT = 6;
    private static final int MAX_TEXT_PREVIEW_LENGTH = 240;

    private FileParsePayloadBuilder() {}

    static Map<String, Object> build(FileParseResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", result.success());
        payload.put("status", result.status().name());
        payload.put("fileName", result.fileName());
        payload.put("fileType", result.fileType());
        payload.put("parser", result.parser());
        payload.put("mode", result.mode().name().toLowerCase());
        payload.put("qualityHint", result.qualityHint());
        payload.put(
                "summary",
                Map.of(
                        "paragraphCount", result.summary().paragraphCount(),
                        "tableCount", result.summary().tableCount(),
                        "sheetCount", result.summary().sheetCount(),
                        "sectionCount", result.summary().sectionCount()));
        payload.put("contentView", buildContentView(result));
        payload.put("warnings", result.warnings());
        payload.put("error", result.error());
        return payload;
    }

    static List<Map<String, Object>> buildAll(List<FileParseResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (FileParseResult result : results) {
            payload.add(build(result));
        }
        return List.copyOf(payload);
    }

    private static Map<String, Object> buildContentView(FileParseResult result) {
        Map<String, Object> contentView = new LinkedHashMap<>();
        contentView.put("contentScope", resolveContentScope(result.mode()));
        if (result.mode() == FileParseMode.TEXT
                && StringUtils.hasText(result.contentView().text())) {
            contentView.put("text", result.contentView().text());
        }
        if (result.mode() == FileParseMode.MARKDOWN
                && StringUtils.hasText(result.contentView().markdown())) {
            contentView.put("markdown", result.contentView().markdown());
        }
        List<Map<String, Object>> sections = result.contentView().sections().stream()
                .limit(MAX_SECTION_COUNT)
                .map(FileParsePayloadBuilder::buildSectionPayload)
                .toList();
        contentView.put("sections", sections);
        contentView.put("sectionCountReturned", sections.size());
        if (result.mode() == FileParseMode.STRUCTURED) {
            contentView.put("schemaOnly", Boolean.TRUE);
        }
        contentView.put("entities", buildEntitiesPayload(result.contentView().entities()));
        return contentView;
    }

    private static Map<String, Object> buildSectionPayload(AttachmentParseResult.Section section) {
        Map<String, Object> sectionMap = new LinkedHashMap<>();
        sectionMap.put("type", section.type());
        sectionMap.put("index", section.index());
        if (StringUtils.hasText(section.name())) {
            sectionMap.put("name", section.name());
        }
        if (section.rowCount() != null) {
            sectionMap.put("rowCount", section.rowCount());
        }
        if (section.columnCount() != null) {
            sectionMap.put("columnCount", section.columnCount());
        }
        if (section.headerRowZeroBasedIndex() != null) {
            sectionMap.put("headerRowZeroBasedIndex", section.headerRowZeroBasedIndex());
        }
        if (!section.header().isEmpty()) {
            sectionMap.put("header", limitStrings(section.header(), MAX_HEADER_COUNT));
        }
        if (!section.sampleRows().isEmpty()) {
            sectionMap.put("sampleRows", limitSampleRows(section.sampleRows()));
        }
        if (!section.columns().isEmpty()) {
            sectionMap.put(
                    "columns",
                    section.columns().stream()
                            .limit(MAX_COLUMN_COUNT)
                            .map(FileParsePayloadBuilder::buildColumnPayload)
                            .toList());
        }
        String textPreview = shrinkText(section.text(), MAX_TEXT_PREVIEW_LENGTH);
        if (StringUtils.hasText(textPreview)) {
            sectionMap.put("textPreview", textPreview);
        }
        return sectionMap;
    }

    private static Map<String, Object> buildColumnPayload(AttachmentParseResult.Column column) {
        Map<String, Object> columnMap = new LinkedHashMap<>();
        columnMap.put("index", column.index());
        columnMap.put("name", column.name());
        columnMap.put("inferredType", column.inferredType());
        columnMap.put("nullCount", column.nullCount());
        columnMap.put("totalCount", column.totalCount());
        if (!column.sampleValues().isEmpty()) {
            columnMap.put("sampleValues", limitStrings(column.sampleValues(), MAX_COLUMN_SAMPLE_VALUE_COUNT));
        }
        return columnMap;
    }

    private static Map<String, Object> buildEntitiesPayload(AttachmentParseResult.Entities entities) {
        Map<String, Object> entitiesMap = new LinkedHashMap<>();
        entitiesMap.put("headings", limitStrings(entities.headings(), MAX_HEADING_COUNT));
        entitiesMap.put("labels", limitStrings(entities.labels(), MAX_LABEL_COUNT));
        entitiesMap.put("sheetNames", limitStrings(entities.sheetNames(), MAX_SHEET_NAME_COUNT));
        entitiesMap.put(
                "tables",
                entities.tables().stream()
                        .limit(MAX_ENTITY_TABLE_COUNT)
                        .map(FileParsePayloadBuilder::buildEntityTablePayload)
                        .toList());
        return entitiesMap;
    }

    private static Map<String, Object> buildEntityTablePayload(AttachmentParseResult.EntityTable table) {
        Map<String, Object> tableMap = new LinkedHashMap<>();
        if (StringUtils.hasText(table.name())) {
            tableMap.put("name", table.name());
        }
        if (!table.header().isEmpty()) {
            tableMap.put("header", limitStrings(table.header(), MAX_HEADER_COUNT));
        }
        if (!table.sampleRows().isEmpty()) {
            tableMap.put("sampleRows", limitSampleRows(table.sampleRows()));
        }
        return tableMap;
    }

    private static List<String> limitStrings(List<String> values, int maxCount) {
        if (values == null || values.isEmpty() || maxCount <= 0) {
            return List.of();
        }
        return values.stream()
                .limit(maxCount)
                .map(value -> value == null ? "" : value)
                .toList();
    }

    private static List<List<String>> limitSampleRows(List<List<String>> sampleRows) {
        if (sampleRows == null || sampleRows.isEmpty()) {
            return List.of();
        }
        return sampleRows.stream()
                .limit(MAX_SAMPLE_ROW_COUNT)
                .map(row -> row == null
                        ? List.<String>of()
                        : row.stream()
                                .limit(MAX_SAMPLE_ROW_COLUMN_COUNT)
                                .map(value -> value == null ? "" : value)
                                .toList())
                .toList();
    }

    private static String resolveContentScope(FileParseMode mode) {
        if (mode == FileParseMode.TEXT) {
            return "full-text";
        }
        if (mode == FileParseMode.MARKDOWN) {
            return "full-markdown";
        }
        return "schema-only";
    }

    private static String shrinkText(String text, int maxLength) {
        if (!StringUtils.hasText(text) || maxLength <= 0) {
            return "";
        }
        String normalized = text.replace("\r", "\n").replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }
}
