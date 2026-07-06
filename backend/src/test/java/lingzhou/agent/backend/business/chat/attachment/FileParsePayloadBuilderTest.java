package lingzhou.agent.backend.business.chat.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileParsePayloadBuilderTest {

    @Test
    void shouldCompactStructuredPayloadToSchemaOnly() {
        FileParseResult result = new FileParseResult(
                true,
                FileParseStatus.SUCCESS,
                "nested.zip",
                "zip",
                "markitdown",
                FileParseMode.STRUCTURED,
                "NORMAL",
                new AttachmentParseResult.Summary(2, 0, 0, 2),
                new FileParseResult.ContentView(
                        "very long full text that should not appear",
                        "# markdown that should not appear",
                        List.of(new AttachmentParseResult.Section(
                                "paragraph",
                                0,
                                "entry-0",
                                "invoice.pdf inside inner.zip under level-2 folder",
                                null,
                                null,
                                null,
                                List.of(),
                                List.of(),
                                List.of())),
                        new AttachmentParseResult.Entities(
                                List.of("压缩包目录"), List.of("PDF", "ZIP"), List.of(), List.of())),
                List.of(),
                "");

        Map<String, Object> payload = FileParsePayloadBuilder.build(result);
        Map<String, Object> contentView = castMap(payload.get("contentView"));
        List<Map<String, Object>> sections = castSectionList(contentView.get("sections"));

        assertThat(contentView).containsEntry("contentScope", "schema-only");
        assertThat(contentView).containsEntry("schemaOnly", true);
        assertThat(contentView).doesNotContainKeys("text", "markdown");
        assertThat(sections).hasSize(1);
        assertThat(sections.get(0)).containsKey("textPreview");
        assertThat(sections.get(0)).doesNotContainKey("text");
    }

    @Test
    void shouldKeepMarkdownBodyButAvoidSectionTextDuplication() {
        FileParseResult result = new FileParseResult(
                true,
                FileParseStatus.SUCCESS,
                "report.docx",
                "docx",
                "poi",
                FileParseMode.MARKDOWN,
                "NORMAL",
                new AttachmentParseResult.Summary(1, 0, 0, 1),
                new FileParseResult.ContentView(
                        "正文",
                        "# 标题\n\n正文",
                        List.of(new AttachmentParseResult.Section(
                                "paragraph", 0, "intro", "正文", null, null, null, List.of(), List.of(), List.of())),
                        AttachmentParseResult.Entities.empty()),
                List.of(),
                "");

        Map<String, Object> payload = FileParsePayloadBuilder.build(result);
        Map<String, Object> contentView = castMap(payload.get("contentView"));
        List<Map<String, Object>> sections = castSectionList(contentView.get("sections"));

        assertThat(contentView).containsEntry("contentScope", "full-markdown");
        assertThat(contentView).containsEntry("markdown", "# 标题\n\n正文");
        assertThat(contentView).doesNotContainKey("text");
        assertThat(sections.get(0)).containsKey("textPreview");
        assertThat(sections.get(0)).doesNotContainKey("text");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castSectionList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
