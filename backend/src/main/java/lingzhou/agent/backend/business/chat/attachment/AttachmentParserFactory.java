package lingzhou.agent.backend.business.chat.attachment;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AttachmentParserFactory {

    private final List<AttachmentParser> parsers;

    public AttachmentParserFactory(List<AttachmentParser> parsers) {
        this.parsers = parsers == null ? List.of() : List.copyOf(parsers);
    }

    public Optional<AttachmentParser> findParser(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }
        return parsers.stream().filter(parser -> parser.supports(fileName)).findFirst();
    }
}
