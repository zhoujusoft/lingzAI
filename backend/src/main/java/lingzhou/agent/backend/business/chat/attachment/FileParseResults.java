package lingzhou.agent.backend.business.chat.attachment;

import java.util.List;
import org.springframework.util.StringUtils;

final class FileParseResults {

    private FileParseResults() {}

    static FileParseResult fromAttachmentResult(AttachmentParseResult result, FileParseMode mode, String parserName) {
        boolean success = result != null && result.success();
        AttachmentParseResult safeResult = result == null ? AttachmentParseResult.failure("", "", "解析结果为空") : result;
        FileParseStatus status = success
                ? (safeResult.warnings().isEmpty() ? FileParseStatus.SUCCESS : FileParseStatus.PARTIAL)
                : FileParseStatus.FAILED;
        String text = safeResult.sections().stream()
                .map(AttachmentParseResult.Section::text)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        String markdown = text;
        return new FileParseResult(
                success,
                status,
                safeResult.fileName(),
                safeResult.fileType(),
                parserName == null ? "" : parserName,
                mode,
                success ? (safeResult.warnings().isEmpty() ? "NORMAL" : "LOW") : "UNKNOWN",
                safeResult.summary(),
                new FileParseResult.ContentView(text, markdown, safeResult.sections(), safeResult.entities()),
                safeResult.warnings(),
                safeResult.error());
    }

    static FileParseResult unsupported(String fileName, String message) {
        return new FileParseResult(
                false,
                FileParseStatus.UNSUPPORTED,
                fileName == null ? "" : fileName,
                "",
                "",
                FileParseMode.STRUCTURED,
                "UNKNOWN",
                AttachmentParseResult.Summary.empty(),
                FileParseResult.ContentView.empty(),
                message == null || message.isBlank() ? List.of() : List.of(message),
                message == null ? "" : message);
    }

    static FileParseResult fromMarkdown(
            String fileName,
            String fileType,
            String parserName,
            FileParseMode mode,
            String markdown,
            List<String> warnings) {
        String safeMarkdown = markdown == null ? "" : markdown.trim();
        List<AttachmentParseResult.Section> sections = MarkdownSectionExtractor.extract(safeMarkdown);
        String text = sections.stream()
                .map(AttachmentParseResult.Section::text)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(safeMarkdown);
        AttachmentParseResult.Summary summary =
                new AttachmentParseResult.Summary(Math.max(sections.size(), 0), 0, 0, sections.size());
        return new FileParseResult(
                true,
                warnings == null || warnings.isEmpty() ? FileParseStatus.SUCCESS : FileParseStatus.PARTIAL,
                fileName == null ? "" : fileName,
                fileType == null ? "" : fileType,
                parserName == null ? "" : parserName,
                mode == null ? FileParseMode.STRUCTURED : mode,
                warnings == null || warnings.isEmpty() ? "NORMAL" : "LOW",
                summary,
                new FileParseResult.ContentView(text, safeMarkdown, sections, AttachmentParseResult.Entities.empty()),
                warnings == null ? List.of() : List.copyOf(warnings),
                "");
    }
}
