package lingzhou.agent.backend.business.chat.attachment;

import java.util.List;

public record AttachmentParseResult(
        boolean success,
        String fileName,
        String fileType,
        Summary summary,
        List<Section> sections,
        Entities entities,
        List<String> warnings,
        String error) {

    public AttachmentParseResult {
        summary = summary == null ? Summary.empty() : summary;
        sections = sections == null ? List.of() : List.copyOf(sections);
        entities = entities == null ? Entities.empty() : entities;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        error = error == null ? "" : error;
    }

    public static AttachmentParseResult failure(String fileName, String fileType, String errorMessage) {
        String normalizedError = errorMessage == null ? "" : errorMessage.trim();
        return new AttachmentParseResult(
                false,
                fileName,
                fileType,
                Summary.empty(),
                List.of(),
                Entities.empty(),
                normalizedError.isEmpty() ? List.of() : List.of(normalizedError),
                normalizedError);
    }

    public record Summary(int paragraphCount, int tableCount, int sheetCount, int sectionCount) {

        public static Summary empty() {
            return new Summary(0, 0, 0, 0);
        }
    }

    public record Section(
            String type,
            int index,
            String name,
            String text,
            Integer rowCount,
            Integer columnCount,
            Integer headerRowIndex,
            List<String> header,
            List<List<String>> sampleRows,
            List<Column> columns) {

        public Section {
            type = type == null ? "" : type;
            name = name == null ? "" : name;
            text = text == null ? "" : text;
            header = header == null ? List.of() : List.copyOf(header);
            sampleRows = sampleRows == null ? List.of() : List.copyOf(sampleRows);
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    public record Column(
            int index,
            String name,
            String inferredType,
            int nullCount,
            int totalCount,
            List<String> sampleValues) {

        public Column {
            name = name == null ? "" : name;
            inferredType = inferredType == null ? "" : inferredType;
            sampleValues = sampleValues == null ? List.of() : List.copyOf(sampleValues);
        }
    }

    public record Entities(List<String> headings, List<String> labels, List<EntityTable> tables, List<String> sheetNames) {

        public Entities {
            headings = headings == null ? List.of() : List.copyOf(headings);
            labels = labels == null ? List.of() : List.copyOf(labels);
            tables = tables == null ? List.of() : List.copyOf(tables);
            sheetNames = sheetNames == null ? List.of() : List.copyOf(sheetNames);
        }

        public static Entities empty() {
            return new Entities(List.of(), List.of(), List.of(), List.of());
        }
    }

    public record EntityTable(String name, List<String> header, List<List<String>> sampleRows) {

        public EntityTable {
            name = name == null ? "" : name;
            header = header == null ? List.of() : List.copyOf(header);
            sampleRows = sampleRows == null ? List.of() : List.copyOf(sampleRows);
        }
    }
}
