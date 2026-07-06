package lingzhou.agent.backend.business.chat.attachment;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

final class MarkdownSectionExtractor {

    private MarkdownSectionExtractor() {}

    static List<AttachmentParseResult.Section> extract(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }
        List<AttachmentParseResult.Section> sections = new ArrayList<>();
        String[] blocks = markdown.split("(\\r?\\n){2,}");
        int index = 0;
        for (String block : blocks) {
            String normalized = block == null ? "" : block.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            String type = normalized.startsWith("#") ? "heading" : "paragraph";
            String name = type.equals("heading")
                    ? normalized.replaceFirst("^#+\\s*", "").trim()
                    : "";
            sections.add(new AttachmentParseResult.Section(
                    type, index++, name, normalized, null, null, null, List.of(), List.of(), List.of()));
        }
        return List.copyOf(sections);
    }
}
