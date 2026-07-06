package lingzhou.agent.backend.business.chat.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lingzhou.agent.backend.business.chat.service.RuntimeFileAssetService;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import lingzhou.agent.backend.framework.authentication.annotation.NotLogin;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ChatFileController {

    private final ChatFileService chatFileService;
    private final RuntimeFileAssetService runtimeFileAssetService;
    private final MinioService minioService;

    public ChatFileController(
            ChatFileService chatFileService,
            RuntimeFileAssetService runtimeFileAssetService,
            MinioService minioService) {
        this.chatFileService = chatFileService;
        this.runtimeFileAssetService = runtimeFileAssetService;
        this.minioService = minioService;
    }

    @PostMapping("/files/upload")
    public ResponseEntity<ChatFileService.UploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "messageId", required = false) Long messageId,
            @RequestParam(value = "eventId", required = false) Long eventId,
            HttpServletRequest request) {
        return chatFileService.upload(
                file, resolveUserId(request), new ChatFileService.UploadBinding(sessionId, messageId, eventId));
    }

    @GetMapping("/files/assets")
    public ChatFileApiModels.FileAssetListResponse listAssets(
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "fileRole", required = false) String fileRole,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request) {
        IPage<RuntimeFileAssetService.RuntimeFileAssetItemView> page =
                runtimeFileAssetService.listAssets(resolveUserId(request), sessionId, fileRole, pageNo, pageSize);
        return ChatFileApiModels.fileAssetList(page);
    }

    @GetMapping("/files/assets/{fileCode}/download")
    public ResponseEntity<byte[]> downloadAsset(@PathVariable("fileCode") String fileCode, HttpServletRequest request)
            throws Exception {
        RuntimeFileAssetService.RuntimeFileAssetBinaryView file =
                runtimeFileAssetService.loadOwnedFileBinary(resolveUserId(request), fileCode, false);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildAttachmentDisposition(file.fileName()))
                .contentType(MediaType.parseMediaType(detectContentType(file.fileName(), file.contentType())))
                .body(file.content());
    }

    @GetMapping("/files/assets/{fileCode}/preview")
    public ResponseEntity<byte[]> previewAsset(@PathVariable("fileCode") String fileCode, HttpServletRequest request)
            throws Exception {
        try {
            RuntimeFileAssetService.RuntimeFileAssetBinaryView file =
                    runtimeFileAssetService.loadOwnedFileBinary(resolveUserId(request), fileCode, true);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            file.inlinePreview()
                                    ? buildInlineDisposition(file.fileName())
                                    : buildAttachmentDisposition(file.fileName()))
                    .contentType(MediaType.parseMediaType(detectContentType(file.fileName(), file.contentType())))
                    .body(file.content());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/files/artifacts/download")
    @NotLogin
    public ResponseEntity<byte[]> downloadArtifact(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "fileName", required = false) String fileName)
            throws Exception {
        if (!StringUtils.hasText(objectName)) {
            return ResponseEntity.badRequest().build();
        }
        String resolvedFileName =
                StringUtils.hasText(fileName) ? fileName.trim() : objectName.substring(objectName.lastIndexOf('/') + 1);
        try (InputStream inputStream = minioService.getFile(objectName)) {
            byte[] content = inputStream.readAllBytes();
            String contentType = detectContentType(resolvedFileName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, buildAttachmentDisposition(resolvedFileName))
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(content);
        }
    }

    @GetMapping("/files/artifacts/{artifactId}/download")
    @NotLogin
    public ResponseEntity<byte[]> downloadArtifactById(
            @PathVariable("artifactId") String artifactId,
            @RequestParam(value = "fileName", required = false) String fileName)
            throws Exception {
        if (!StringUtils.hasText(artifactId)) {
            return ResponseEntity.badRequest().build();
        }
        String objectName = minioService.fromArtifactId(artifactId.trim());
        String resolvedFileName =
                StringUtils.hasText(fileName) ? fileName.trim() : objectName.substring(objectName.lastIndexOf('/') + 1);
        try (InputStream inputStream = minioService.getFile(objectName)) {
            byte[] content = inputStream.readAllBytes();
            String contentType = detectContentType(resolvedFileName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, buildAttachmentDisposition(resolvedFileName))
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(content);
        }
    }

    @GetMapping("/files/artifacts/{artifactId}/preview")
    @NotLogin
    public ResponseEntity<byte[]> previewArtifactById(
            @PathVariable("artifactId") String artifactId,
            @RequestParam(value = "fileName", required = false) String fileName)
            throws Exception {
        if (!StringUtils.hasText(artifactId)) {
            return ResponseEntity.badRequest().build();
        }
        String objectName = minioService.fromArtifactId(artifactId.trim());
        String resolvedFileName =
                StringUtils.hasText(fileName) ? fileName.trim() : objectName.substring(objectName.lastIndexOf('/') + 1);
        try (InputStream inputStream = minioService.getFile(objectName)) {
            byte[] content = inputStream.readAllBytes();
            String contentType = detectContentType(resolvedFileName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, buildInlineDisposition(resolvedFileName))
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(content);
        }
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object value = request.getAttribute("UserId");
        if (value == null) {
            throw new IllegalStateException("UserId missing");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String detectContentType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF_VALUE;
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html";
        }
        if (lower.endsWith(".md") || lower.endsWith(".txt")) {
            return "text/plain";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String detectContentType(String fileName, String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim() : detectContentType(fileName);
    }

    private String buildAttachmentDisposition(String fileName) {
        String resolvedFileName = StringUtils.hasText(fileName) ? fileName.trim() : "download";
        return ContentDisposition.attachment()
                .filename(resolvedFileName, StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    private String buildInlineDisposition(String fileName) {
        String resolvedFileName = StringUtils.hasText(fileName) ? fileName.trim() : "preview";
        return ContentDisposition.inline()
                .filename(resolvedFileName, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
