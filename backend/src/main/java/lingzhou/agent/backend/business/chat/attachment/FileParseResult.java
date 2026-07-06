package lingzhou.agent.backend.business.chat.attachment;

import java.util.List;

public record FileParseResult(
        boolean success,
        FileParseStatus status,
        String fileName,
        String fileType,
        String parser,
        FileParseMode mode,
        String qualityHint,
        AttachmentParseResult.Summary summary,
        ContentView contentView,
        List<String> warnings,
        String error) {

    public FileParseResult {
        status = status == null ? FileParseStatus.FAILED : status;
        fileName = fileName == null ? "" : fileName;
        fileType = fileType == null ? "" : fileType;
        parser = parser == null ? "" : parser;
        mode = mode == null ? FileParseMode.STRUCTURED : mode;
        qualityHint = qualityHint == null ? "UNKNOWN" : qualityHint;
        summary = summary == null ? AttachmentParseResult.Summary.empty() : summary;
        contentView = contentView == null ? ContentView.empty() : contentView;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        error = error == null ? "" : error;
    }

    public record ContentView(
            String text,
            String markdown,
            List<AttachmentParseResult.Section> sections,
            AttachmentParseResult.Entities entities) {

        public ContentView {
            text = text == null ? "" : text;
            markdown = markdown == null ? "" : markdown;
            sections = sections == null ? List.of() : List.copyOf(sections);
            entities = entities == null ? AttachmentParseResult.Entities.empty() : entities;
        }

        public static ContentView empty() {
            return new ContentView("", "", List.of(), AttachmentParseResult.Entities.empty());
        }
    }
}
