package lingzhou.agent.backend.business.chat.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChatFileService {

    private static final Logger logger = LoggerFactory.getLogger(ChatFileService.class);
    private static final String CHAT_UPLOAD_PATH_PREFIX = "chat-upload://";

    private final Map<String, UploadedFile> uploadedFiles = new ConcurrentHashMap<>();
    private final MinioService minioService;

    public ChatFileService(MinioService minioService) {
        this.minioService = minioService;
    }

    public ResponseEntity<UploadResponse> upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new UploadResponse(null, null, 0, "File is empty"));
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename());
        if (originalName.isBlank()) {
            originalName = "file";
        }
        String id = UUID.randomUUID().toString();
        long safeUserId = userId == null || userId <= 0 ? 0L : userId;
        try {
            String objectName = minioService.uploadChatFile(file, safeUserId, id);
            UploadedFile uploaded = new UploadedFile(id, originalName, toChatUploadPath(objectName), file.getSize(), objectName);
            uploadedFiles.put(id, uploaded);
            return ResponseEntity.ok(new UploadResponse(id, originalName, file.getSize(), null));
        } catch (Exception e) {
            logger.error("聊天附件上传失败：userId={}, fileId={}, error={}", safeUserId, id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadResponse(null, null, 0, "Upload failed"));
        }
    }

    public List<UploadedFile> resolveFiles(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return fileIds.stream().map(uploadedFiles::get).filter(item -> item != null).toList();
    }

    public String buildFileListJson(List<String> fileIds) {
        List<UploadedFile> files = resolveFiles(fileIds);
        if (files.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> payload = files.stream().map(item -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", item.id());
            map.put("name", item.name());
            map.put("path", item.path());
            map.put("size", item.size());
            map.put("objectName", item.objectName());
            return map;
        }).toList();
        return JSON.toJSONString(payload);
    }

    public String buildUserMessage(String base, List<String> fileIds, boolean allowReadFile) {
        String content = StringUtils.hasText(base) ? base.trim() : "";
        List<UploadedFile> files = resolveFiles(fileIds);
        if (files.isEmpty()) {
            return content;
        }
        String fileList = files.stream().map(file -> {
            if (allowReadFile) {
                return "- " + file.name() + " (" + file.path() + ")";
            }
            return "- " + file.name();
        }).reduce((left, right) -> left + "\n" + right).orElse("");

        String prefix = content.isEmpty() ? "" : content + "\n\n";
        if (allowReadFile) {
            return prefix + "User uploaded files:\n" + fileList + "\n\nYou can call readFile(path) if needed.";
        }
        return prefix + "User uploaded files:\n" + fileList;
    }

    public String readFileAsString(String pathValue) {
        String objectName = extractChatObjectName(pathValue);
        if (!StringUtils.hasText(objectName)) {
            return errorJson("Unsupported chat upload path: " + pathValue);
        }
        try (InputStream inputStream = minioService.getFile(objectName)) {
            String fileName = determineFileName(pathValue, objectName);
            if (fileName.toLowerCase().endsWith(".docx")) {
                try (XWPFDocument document = new XWPFDocument(inputStream);
                        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText();
                }
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            logger.warn("读取聊天附件失败：objectName={}, error={}", objectName, ex.getMessage(), ex);
            return errorJson("Read failed: " + ex.getMessage());
        }
    }

    public Path materializeToLocalPath(String pathValue) throws IOException {
        String objectName = extractChatObjectName(pathValue);
        if (!StringUtils.hasText(objectName)) {
            throw new IOException("Unsupported chat upload path: " + pathValue);
        }

        String fileName = determineFileName(pathValue, objectName);
        String suffix = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            suffix = fileName.substring(dotIndex);
        }

        Path tempFile = Files.createTempFile("chat-upload-", suffix);
        tempFile.toFile().deleteOnExit();
        try (InputStream inputStream = minioService.getFile(objectName)) {
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // ignore cleanup failure
            }
            throw new IOException("Failed to materialize chat upload: " + ex.getMessage(), ex);
        }
        return tempFile;
    }

    public InputStream openInputStream(UploadedFile uploadedFile) throws IOException {
        if (uploadedFile == null || !StringUtils.hasText(uploadedFile.objectName())) {
            throw new IOException("Uploaded file metadata is missing");
        }
        try {
            return minioService.getFile(uploadedFile.objectName());
        } catch (Exception ex) {
            throw new IOException("Failed to open chat upload: " + ex.getMessage(), ex);
        }
    }

    public void deletePersistedFiles(List<String> fileListJsons) {
        if (fileListJsons == null || fileListJsons.isEmpty()) {
            return;
        }
        for (String fileListJson : fileListJsons) {
            if (!StringUtils.hasText(fileListJson)) {
                continue;
            }
            try {
                List<Map<String, Object>> items =
                        JSON.parseObject(fileListJson, new TypeReference<List<Map<String, Object>>>() {});
                if (items == null || items.isEmpty()) {
                    continue;
                }
                for (Map<String, Object> item : items) {
                    deletePersistedFile(item);
                }
            } catch (Exception ex) {
                logger.warn("解析会话附件元数据失败，跳过清理：error={}", ex.getMessage(), ex);
            }
        }
    }

    public record UploadResponse(String id, String name, long size, String error) {}

    public record UploadedFile(String id, String name, String path, long size, String objectName) {}

    static boolean isChatUploadPath(String pathValue) {
        return StringUtils.hasText(pathValue) && pathValue.startsWith(CHAT_UPLOAD_PATH_PREFIX);
    }

    static String toChatUploadPath(String objectName) {
        return CHAT_UPLOAD_PATH_PREFIX + objectName;
    }

    static String extractChatObjectName(String pathValue) {
        if (!isChatUploadPath(pathValue)) {
            return "";
        }
        return pathValue.substring(CHAT_UPLOAD_PATH_PREFIX.length()).trim();
    }

    private void deletePersistedFile(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return;
        }
        String id = asString(item.get("id"));
        String objectName = asString(item.get("objectName"));
        String path = asString(item.get("path"));
        if (!StringUtils.hasText(objectName)) {
            objectName = extractChatObjectName(path);
        }
        if (StringUtils.hasText(objectName)) {
            try {
                minioService.deleteFile(objectName);
            } catch (Exception ex) {
                logger.warn("删除聊天附件失败，继续删除会话：objectName={}, error={}", objectName, ex.getMessage(), ex);
            }
        } else if (StringUtils.hasText(path)) {
            deleteLegacyLocalFile(path);
        }
        if (StringUtils.hasText(id)) {
            uploadedFiles.remove(id);
        }
    }

    private void deleteLegacyLocalFile(String pathValue) {
        try {
            Path path = Path.of(pathValue).toAbsolutePath().normalize();
            if (java.nio.file.Files.deleteIfExists(path)) {
                logger.info("删除历史本地聊天附件成功：path={}", path);
            }
        } catch (IOException | RuntimeException ex) {
            logger.warn("删除历史本地聊天附件失败，继续删除会话：path={}, error={}", pathValue, ex.getMessage(), ex);
        }
    }

    private String determineFileName(String pathValue, String objectName) {
        for (UploadedFile uploadedFile : uploadedFiles.values()) {
            if (objectName.equals(uploadedFile.objectName()) || pathValue.equals(uploadedFile.path())) {
                return uploadedFile.name();
            }
        }
        int lastSlash = objectName.lastIndexOf('/');
        return lastSlash >= 0 ? objectName.substring(lastSlash + 1) : objectName;
    }

    private String errorJson(String message) {
        return """
                {
                  "success": false,
                  "error": "%s"
                }
                """
                .formatted(escapeJson(message));
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
