package lingzhou.agent.backend.business.chat.attachment;

import java.util.List;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SpecializedAttachmentParseProvider implements FileParseProvider {

    private final AttachmentParseService attachmentParseService;

    public SpecializedAttachmentParseProvider(AttachmentParseService attachmentParseService) {
        this.attachmentParseService = attachmentParseService;
    }

    @Override
    public String name() {
        return "specialized-parser";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean supports(ChatFileService.UploadedFile file, FileParseMode mode) {
        if (file == null || !StringUtils.hasText(file.name())) {
            return false;
        }
        List<AttachmentParseResult> parsed = attachmentParseService.parseResolvedFiles(List.of(file));
        return parsed != null && !parsed.isEmpty();
    }

    @Override
    public FileParseResult parse(ChatFileService.UploadedFile file, FileParseMode mode) {
        List<AttachmentParseResult> parsed = attachmentParseService.parseResolvedFiles(List.of(file));
        if (parsed == null || parsed.isEmpty()) {
            return FileParseResults.unsupported(
                    file == null ? "" : file.name(), "当前附件暂无可用解析器: " + (file == null ? "" : file.name()));
        }
        return FileParseResults.fromAttachmentResult(parsed.get(0), mode, name());
    }
}
