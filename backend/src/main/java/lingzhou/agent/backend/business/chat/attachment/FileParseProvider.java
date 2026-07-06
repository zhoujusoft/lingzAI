package lingzhou.agent.backend.business.chat.attachment;

import lingzhou.agent.backend.business.chat.service.ChatFileService;

public interface FileParseProvider {

    String name();

    default int order() {
        return 100;
    }

    boolean supports(ChatFileService.UploadedFile file, FileParseMode mode);

    FileParseResult parse(ChatFileService.UploadedFile file, FileParseMode mode);
}
