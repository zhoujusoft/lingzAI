package lingzhou.agent.backend.business.chat.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lingzhou.agent.backend.business.chat.util.UlidGenerator;
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
    private static final String RUNTIME_LOCAL_PATH_PREFIX = "runtime-local://";
    private static final Set<String> IMAGE_UPLOAD_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp", ".gif");
    private static final Set<String> ARCHIVE_UPLOAD_EXTENSIONS = Set.of(".zip");
    private static final Set<String> DOCUMENT_UPLOAD_EXTENSIONS =
            Set.of(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".csv", ".txt", ".md", ".ppt", ".pptx");
    private static final Set<String> ALLOWED_UPLOAD_EXTENSIONS = buildAllowedUploadExtensions();
    private static final Set<String> FORBIDDEN_UPLOAD_EXTENSIONS = Set.of(
            ".py",
            ".pyw",
            ".sh",
            ".bash",
            ".zsh",
            ".command",
            ".bat",
            ".cmd",
            ".ps1",
            ".js",
            ".mjs",
            ".cjs",
            ".ts",
            ".jar",
            ".exe",
            ".app",
            ".msi");
    private static final long MAX_IMAGE_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PDF_UPLOAD_BYTES = 50L * 1024 * 1024;
    private static final long MAX_DOCUMENT_UPLOAD_BYTES = 20L * 1024 * 1024;
    private static final long MAX_ARCHIVE_UPLOAD_BYTES = 100L * 1024 * 1024;

    private final Map<String, UploadedFile> uploadedFiles = new ConcurrentHashMap<>();
    private final MinioService minioService;
    private final RuntimeFileAssetService runtimeFileAssetService;

    public ChatFileService(MinioService minioService, RuntimeFileAssetService runtimeFileAssetService) {
        this.minioService = minioService;
        this.runtimeFileAssetService = runtimeFileAssetService;
    }

    public ResponseEntity<UploadResponse> upload(MultipartFile file, Long userId, UploadBinding binding) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new UploadResponse(null, null, 0, "File is empty", null));
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename());
        if (originalName.isBlank()) {
            originalName = "file";
        }
        if (isForbiddenUploadFile(originalName)) {
            return ResponseEntity.badRequest().body(new UploadResponse(null, null, 0, "不允许上传脚本或可执行文件", null));
        }
        String extension = resolveUploadExtension(originalName);
        if (!isAllowedUploadFile(originalName)) {
            return ResponseEntity.badRequest().body(new UploadResponse(null, null, 0, "仅支持图片、常用文档和 ZIP 压缩包上传", null));
        }
        long maxBytes = resolveMaxUploadBytes(extension);
        if (file.getSize() > maxBytes) {
            return ResponseEntity.badRequest()
                    .body(new UploadResponse(
                            null,
                            null,
                            0,
                            originalName + " 超过大小限制，最大允许 " + resolveMaxUploadSizeLabel(extension),
                            null));
        }
        String id = UlidGenerator.next();
        long safeUserId = userId == null || userId <= 0 ? 0L : userId;
        String objectName = null;
        try {
            objectName = minioService.uploadChatFile(file, safeUserId, id);
            runtimeFileAssetService.recordUpload(
                    file,
                    safeUserId,
                    id,
                    objectName,
                    binding == null
                            ? null
                            : new RuntimeFileAssetService.UploadBinding(
                                    binding.sessionCode(), binding.messageId(), binding.eventId()),
                    minioService.getBucketName(),
                    file.getContentType());
            UploadedFile uploaded =
                    new UploadedFile(id, originalName, toChatUploadPath(objectName), file.getSize(), objectName);
            uploadedFiles.put(id, uploaded);
            return ResponseEntity.ok(new UploadResponse(
                    id,
                    originalName,
                    file.getSize(),
                    null,
                    minioService.toChatUploadDescriptor(id, originalName, file.getSize(), objectName)));
        } catch (Exception e) {
            return handleUploadFailure(safeUserId, id, objectName, e);
        }
    }

    public UploadResponse uploadBytes(
            String originalName, byte[] content, String contentType, Long userId, UploadBinding binding) {
        ResponseEntity<UploadResponse> response =
                upload(new InMemoryMultipartFile(originalName, content, contentType), userId, binding);
        UploadResponse body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful()) {
            String error = body == null || !StringUtils.hasText(body.error()) ? "Upload failed" : body.error();
            throw new IllegalArgumentException(error);
        }
        if (body == null || !StringUtils.hasText(body.id())) {
            throw new IllegalStateException("Upload failed: missing fileId");
        }
        return body;
    }

    public record UploadBinding(String sessionCode, Long messageId, Long eventId) {}

    private ResponseEntity<UploadResponse> handleUploadFailure(
            Long userId, String fileId, String objectName, Exception e) {
        if (StringUtils.hasText(objectName)) {
            try {
                minioService.deleteFile(objectName);
            } catch (Exception deleteEx) {
                logger.warn(
                        "清理失败的聊天附件对象失败：userId={}, fileId={}, objectName={}, error={}",
                        userId,
                        fileId,
                        objectName,
                        deleteEx.getMessage(),
                        deleteEx);
            }
        }
        logger.error("聊天附件上传失败：userId={}, fileId={}, error={}", userId, fileId, e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new UploadResponse(null, null, 0, "Upload failed", null));
    }

    public List<UploadedFile> resolveFiles(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return fileIds.stream()
                .map(uploadedFiles::get)
                .filter(item -> item != null)
                .toList();
    }

    public List<UploadedFile> resolveFilesFromFileListJson(String fileListJson) {
        if (!StringUtils.hasText(fileListJson)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> items =
                    JSON.parseObject(fileListJson, new TypeReference<List<Map<String, Object>>>() {});
            if (items == null || items.isEmpty()) {
                return List.of();
            }
            List<UploadedFile> files = new java.util.ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item == null || item.isEmpty()) {
                    continue;
                }
                String id = asString(item.get("id"));
                String name = asString(item.get("name"));
                String path = asString(item.get("path"));
                String objectName = asString(item.get("objectName"));
                long size = parseLong(item.get("size"));
                if (!StringUtils.hasText(name) || !StringUtils.hasText(objectName)) {
                    continue;
                }
                String resolvedPath = StringUtils.hasText(path) ? path : toChatUploadPath(objectName);
                files.add(new UploadedFile(id, name, resolvedPath, size, objectName));
            }
            return List.copyOf(files);
        } catch (Exception ex) {
            logger.warn("解析 fileListJson 失败：error={}", ex.getMessage(), ex);
            return List.of();
        }
    }

    public String canonicalizeLogicalUploadPath(String fileListJson, String pathValue) {
        if (!StringUtils.hasText(pathValue)) {
            return pathValue;
        }
        String normalized = pathValue.trim().replace("\\", "/");
        if (!normalized.startsWith("/uploads/")) {
            return normalized;
        }
        List<UploadedFile> files = resolveFilesFromFileListJson(fileListJson);
        if (files.isEmpty()) {
            return normalized;
        }
        UploadedFile exact = matchLogicalUpload(files, normalized);
        if (exact != null && StringUtils.hasText(exact.name())) {
            return "/uploads/" + exact.name();
        }
        if (files.size() == 1 && StringUtils.hasText(files.get(0).name())) {
            return "/uploads/" + files.get(0).name();
        }
        UploadedFile aliasMatched = matchByNormalizedStem(files, normalized.substring("/uploads/".length()));
        if (aliasMatched != null && StringUtils.hasText(aliasMatched.name())) {
            return "/uploads/" + aliasMatched.name();
        }
        return normalized;
    }

    public List<String> canonicalizeLogicalUploadPaths(String fileListJson, List<String> pathValues) {
        if (pathValues == null || pathValues.isEmpty()) {
            return List.of();
        }
        return pathValues.stream()
                .map(path -> canonicalizeLogicalUploadPath(fileListJson, path))
                .toList();
    }

    public String buildFileListJson(List<String> fileIds) {
        List<UploadedFile> files = resolveFiles(fileIds);
        if (files.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> payload = files.stream()
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", item.id());
                    map.put("name", item.name());
                    map.put("path", item.path());
                    map.put("size", item.size());
                    map.put("objectName", item.objectName());
                    return map;
                })
                .toList();
        return JSON.toJSONString(payload);
    }

    public String buildUserMessage(String base, List<String> fileIds, boolean allowReadFile) {
        String content = StringUtils.hasText(base) ? base.trim() : "";
        List<UploadedFile> files = resolveFiles(fileIds);
        if (files.isEmpty()) {
            return content;
        }
        String fileList = files.stream()
                .map(file -> {
                    if (allowReadFile) {
                        return "- " + file.name() + " (/uploads/" + file.name() + ")";
                    }
                    return "- " + file.name();
                })
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        String prefix = content.isEmpty() ? "" : content + "\n\n";
        if (allowReadFile) {
            return prefix + "User uploaded files:\n" + fileList
                    + "\n\nUploaded files are available under /uploads/ ."
                    + " For UTF-8 text files, you may call the direct runtime tool `file_read`."
                    + " For binary files such as .docx/.pdf/.xlsx, do not treat them as plain text; prefer parse_file for content understanding, or direct file processing via scripts.";
        }
        return prefix + "User uploaded files:\n" + fileList;
    }

    private UploadedFile matchLogicalUpload(List<UploadedFile> files, String logicalPath) {
        if (files == null || files.isEmpty() || !StringUtils.hasText(logicalPath)) {
            return null;
        }
        String fileName = logicalPath.substring("/uploads/".length()).trim();
        for (UploadedFile file : files) {
            if (file == null || !StringUtils.hasText(file.name())) {
                continue;
            }
            if (fileName.equals(file.name()) || logicalPath.equals("/uploads/" + file.name())) {
                return file;
            }
        }
        return null;
    }

    private UploadedFile matchByNormalizedStem(List<UploadedFile> files, String requestedFileName) {
        if (files == null || files.isEmpty() || !StringUtils.hasText(requestedFileName)) {
            return null;
        }
        String requestedStem = normalizeFileStem(requestedFileName);
        if (!StringUtils.hasText(requestedStem)) {
            return null;
        }
        for (UploadedFile file : files) {
            if (file == null || !StringUtils.hasText(file.name())) {
                continue;
            }
            String candidateStem = normalizeFileStem(file.name());
            if (!StringUtils.hasText(candidateStem)) {
                continue;
            }
            if (candidateStem.equals(requestedStem)
                    || candidateStem.contains(requestedStem)
                    || requestedStem.contains(candidateStem)) {
                return file;
            }
        }
        return null;
    }

    private String normalizeFileStem(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        String baseName = fileName.trim();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < baseName.length(); index += 1) {
            char current = baseName.charAt(index);
            if (Character.isLetterOrDigit(current) || Character.UnicodeScript.of(current) == Character.UnicodeScript.HAN) {
                builder.append(Character.toLowerCase(current));
            }
        }
        return builder.toString();
    }

    public void materializePersistedFiles(String fileListJson, Path targetDir) throws IOException {
        if (!StringUtils.hasText(fileListJson) || targetDir == null) {
            return;
        }
        Files.createDirectories(targetDir);
        List<Map<String, Object>> items =
                JSON.parseObject(fileListJson, new TypeReference<List<Map<String, Object>>>() {});
        if (items == null || items.isEmpty()) {
            return;
        }
        int index = 0;
        for (Map<String, Object> item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            String objectName = asString(item.get("objectName"));
            String fileName = determinePersistedFileName(item, ++index);
            if (!StringUtils.hasText(objectName) || !StringUtils.hasText(fileName)) {
                continue;
            }
            Path target = resolveMaterializedUploadPath(targetDir, fileName);
            try (InputStream inputStream = minioService.getFile(objectName)) {
                Files.copy(inputStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                throw new IOException("物化上传文件失败: " + fileName + ", " + ex.getMessage(), ex);
            }
        }
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
        if (StringUtils.hasText(objectName)) {
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
                return tempFile;
            } catch (Exception ex) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // ignore cleanup failure
                }
                throw new IOException("Failed to materialize chat upload: " + ex.getMessage(), ex);
            }
        }
        Path runtimeLocalPath = extractRuntimeLocalPath(pathValue);
        if (runtimeLocalPath != null) {
            return materializeLocalFile(runtimeLocalPath, "runtime-local-");
        }
        throw new IOException("Unsupported chat upload path: " + pathValue);
    }

    public InputStream openInputStream(UploadedFile uploadedFile) throws IOException {
        if (uploadedFile == null) {
            throw new IOException("Uploaded file metadata is missing");
        }
        if (StringUtils.hasText(uploadedFile.objectName())) {
            try {
                return minioService.getFile(uploadedFile.objectName());
            } catch (Exception ex) {
                throw new IOException("Failed to open chat upload: " + ex.getMessage(), ex);
            }
        }
        Path runtimeLocalPath = extractRuntimeLocalPath(uploadedFile.path());
        if (runtimeLocalPath != null) {
            return Files.newInputStream(runtimeLocalPath);
        }
        throw new IOException("Uploaded file metadata is missing");
    }

    public UploadedFile createRuntimeLocalUploadedFile(String logicalPath, Path hostPath) throws IOException {
        if (hostPath == null) {
            throw new IOException("Runtime local file path is missing");
        }
        Path normalized = hostPath.toAbsolutePath().normalize();
        if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
            throw new IOException("Runtime local file does not exist: " + normalized);
        }
        String fileName = StringUtils.hasText(logicalPath)
                ? Path.of(logicalPath).getFileName().toString()
                : normalized.getFileName().toString();
        return new UploadedFile(null, fileName, toRuntimeLocalPath(normalized), Files.size(normalized), null);
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

    public record UploadResponse(
            String id, String name, long size, String error, MinioService.StoredFileDescriptor file) {}

    public record UploadedFile(String id, String name, String path, long size, String objectName) {}

    static boolean isChatUploadPath(String pathValue) {
        return StringUtils.hasText(pathValue) && pathValue.startsWith(CHAT_UPLOAD_PATH_PREFIX);
    }

    static boolean isRuntimeLocalPath(String pathValue) {
        return StringUtils.hasText(pathValue) && pathValue.startsWith(RUNTIME_LOCAL_PATH_PREFIX);
    }

    static String toChatUploadPath(String objectName) {
        return CHAT_UPLOAD_PATH_PREFIX + objectName;
    }

    static String toRuntimeLocalPath(Path hostPath) {
        return RUNTIME_LOCAL_PATH_PREFIX + hostPath.toAbsolutePath().normalize();
    }

    static String extractChatObjectName(String pathValue) {
        if (!isChatUploadPath(pathValue)) {
            return "";
        }
        return pathValue.substring(CHAT_UPLOAD_PATH_PREFIX.length()).trim();
    }

    static Path extractRuntimeLocalPath(String pathValue) {
        if (!isRuntimeLocalPath(pathValue)) {
            return null;
        }
        String rawPath = pathValue.substring(RUNTIME_LOCAL_PATH_PREFIX.length()).trim();
        if (!StringUtils.hasText(rawPath)) {
            return null;
        }
        return Path.of(rawPath).toAbsolutePath().normalize();
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
        return fallbackFileName(objectName);
    }

    private String determinePersistedFileName(Map<String, Object> item, int index) {
        String fileName = asString(item.get("name"));
        if (!StringUtils.hasText(fileName)) {
            fileName = fallbackFileName(asString(item.get("objectName")));
        }
        if (!StringUtils.hasText(fileName)) {
            fileName = "upload-" + index;
        }
        return StringUtils.cleanPath(fileName);
    }

    private Path resolveMaterializedUploadPath(Path targetDir, String fileName) {
        Path candidate = targetDir.resolve(fileName).normalize();
        if (!candidate.startsWith(targetDir.normalize())) {
            return targetDir.resolve("upload-" + Math.abs(fileName.hashCode())).normalize();
        }
        return candidate;
    }

    private String fallbackFileName(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return "";
        }
        int lastSlash = objectName.lastIndexOf('/');
        return lastSlash >= 0 ? objectName.substring(lastSlash + 1) : objectName;
    }

    private boolean isForbiddenUploadFile(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String normalized = fileName.trim().toLowerCase(Locale.ROOT);
        for (String extension : FORBIDDEN_UPLOAD_EXTENSIONS) {
            if (normalized.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedUploadFile(String fileName) {
        String extension = resolveUploadExtension(fileName);
        return StringUtils.hasText(extension) && ALLOWED_UPLOAD_EXTENSIONS.contains(extension);
    }

    private String resolveUploadExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        String normalized = fileName.trim().toLowerCase(Locale.ROOT);
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return normalized.substring(dotIndex);
    }

    private long resolveMaxUploadBytes(String extension) {
        if (IMAGE_UPLOAD_EXTENSIONS.contains(extension)) {
            return MAX_IMAGE_UPLOAD_BYTES;
        }
        if (".pdf".equals(extension)) {
            return MAX_PDF_UPLOAD_BYTES;
        }
        if (ARCHIVE_UPLOAD_EXTENSIONS.contains(extension)) {
            return MAX_ARCHIVE_UPLOAD_BYTES;
        }
        return MAX_DOCUMENT_UPLOAD_BYTES;
    }

    private String resolveMaxUploadSizeLabel(String extension) {
        if (IMAGE_UPLOAD_EXTENSIONS.contains(extension)) {
            return "10MB";
        }
        if (".pdf".equals(extension)) {
            return "50MB";
        }
        if (ARCHIVE_UPLOAD_EXTENSIONS.contains(extension)) {
            return "100MB";
        }
        return "20MB";
    }

    private static Set<String> buildAllowedUploadExtensions() {
        LinkedHashSet<String> extensions = new LinkedHashSet<>();
        extensions.addAll(IMAGE_UPLOAD_EXTENSIONS);
        extensions.addAll(ARCHIVE_UPLOAD_EXTENSIONS);
        extensions.addAll(DOCUMENT_UPLOAD_EXTENSIONS);
        return Set.copyOf(extensions);
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

    private static final class InMemoryMultipartFile implements MultipartFile {

        private final String originalName;
        private final byte[] content;
        private final String contentType;

        private InMemoryMultipartFile(String originalName, byte[] content, String contentType) {
            this.originalName = StringUtils.hasText(originalName) ? StringUtils.cleanPath(originalName) : "file";
            this.content = content == null ? new byte[0] : content.clone();
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalName;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            if (dest == null) {
                throw new IOException("Destination file is missing");
            }
            Files.write(dest.toPath(), content);
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ex) {
            return 0L;
        }
    }

    private Path materializeLocalFile(Path sourcePath, String prefix) throws IOException {
        if (sourcePath == null || !Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new IOException("Local file does not exist: " + sourcePath);
        }
        String fileName = sourcePath.getFileName() == null
                ? "file"
                : sourcePath.getFileName().toString();
        String suffix = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            suffix = fileName.substring(dotIndex);
        }
        Path tempFile = Files.createTempFile(prefix, suffix);
        tempFile.toFile().deleteOnExit();
        try {
            Files.copy(sourcePath, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        } catch (Exception ex) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // ignore cleanup failure
            }
            throw new IOException("Failed to materialize local file: " + ex.getMessage(), ex);
        }
    }
}
