package lingzhou.agent.backend.business.chat.attachment;

import java.nio.file.Files;
import java.nio.file.Path;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ZipStructuredParseProvider implements FileParseProvider {

    private final ChatFileService chatFileService;
    private final ZipStructuredSchemaParser schemaParser = new ZipStructuredSchemaParser();

    public ZipStructuredParseProvider(ChatFileService chatFileService) {
        this.chatFileService = chatFileService;
    }

    @Override
    public String name() {
        return "zip-schema";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(ChatFileService.UploadedFile file, FileParseMode mode) {
        return mode == FileParseMode.STRUCTURED && "zip".equals(extension(file == null ? null : file.name()));
    }

    @Override
    public FileParseResult parse(ChatFileService.UploadedFile file, FileParseMode mode) {
        if (file == null || !StringUtils.hasText(file.name())) {
            return FileParseResults.unsupported("", "上传附件信息为空");
        }
        Path tempFile = null;
        try {
            tempFile = chatFileService.materializeToLocalPath(file.path());
            AttachmentParseResult attachmentParseResult = schemaParser.parse(tempFile, file.name());
            return FileParseResults.fromAttachmentResult(attachmentParseResult, FileParseMode.STRUCTURED, name());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "ZIP schema 解析失败" : ex.getMessage();
            return new FileParseResult(
                    false,
                    FileParseStatus.FAILED,
                    file.name(),
                    extension(file.name()),
                    name(),
                    FileParseMode.STRUCTURED,
                    "UNKNOWN",
                    AttachmentParseResult.Summary.empty(),
                    FileParseResult.ContentView.empty(),
                    java.util.List.of(message),
                    message);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                    // ignore cleanup failure
                }
            }
        }
    }

    private String extension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).trim().toLowerCase();
    }
}
