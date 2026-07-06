package lingzhou.agent.backend.business.datasets.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * MinIO 文件存储服务
 */
@Component
@Slf4j
public class MinioService {

    @Value("${minio.endpoint:http://minio:9000}")
    private String endpoint;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${minio.bucket:documents}")
    private String bucket;

    private MinioClient minioClient;

    @PostConstruct
    public void init() throws Exception {
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        // 确保 bucket 存在
        boolean found = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("创建 MinIO bucket: {}", bucket);
        }
    }

    /**
     * 上传文件
     *
     * @param file 文件
     * @param kbId 知识库 ID
     * @param docId 文档 ID
     * @return 对象名称（路径）
     */
    public String uploadFile(MultipartFile file, Long kbId, Long docId) throws Exception {
        String objectName =
                String.format("documents/%d/%d/%s%s", kbId, docId, java.util.UUID.randomUUID(), extractExtension(file));
        putMultipartFile(objectName, file);
        log.info("文件上传成功：bucket={}, objectName={}, size={}", bucket, objectName, file.getSize());
        return objectName;
    }

    public String uploadChatFile(MultipartFile file, Long userId, String fileId) throws Exception {
        long safeUserId = userId == null || userId <= 0 ? 0L : userId;
        String objectName = String.format("chat-files/%d/%s%s", safeUserId, fileId, extractExtension(file));
        putMultipartFile(objectName, file);
        log.info("聊天附件上传成功：bucket={}, objectName={}, size={}", bucket, objectName, file.getSize());
        return objectName;
    }

    public String getBucketName() {
        return bucket;
    }

    /**
     * 获取文件输入流
     *
     * @param objectName 对象名称
     * @return 输入流
     */
    public InputStream getFile(String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectName).build());
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名称
     */
    public void deleteFile(String objectName) throws Exception {
        minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
        log.info("文件删除成功：bucket={}, objectName={}", bucket, objectName);
    }

    public ArtifactUploadResult uploadArtifact(String folder, String fileName, byte[] content, String contentType)
            throws Exception {
        String objectName = buildArtifactObjectName(folder, fileName);
        byte[] safeContent = content == null ? new byte[0] : content;
        String resolvedFileName = StringUtils.hasText(fileName) ? fileName.trim() : "artifact";
        String resolvedContentType = resolveContentType(contentType, resolvedFileName);
        minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectName).stream(
                        new ByteArrayInputStream(safeContent), safeContent.length, -1)
                .contentType(resolvedContentType)
                .build());
        log.info("产物上传成功：bucket={}, objectName={}, size={}", bucket, objectName, safeContent.length);
        return new ArtifactUploadResult(
                bucket, objectName, resolvedFileName, toArtifactPath(objectName), resolvedContentType);
    }

    public ArtifactUploadResult uploadArtifact(Path sourcePath, String folder, String fileName, String contentType)
            throws Exception {
        if (sourcePath == null || !Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException("artifact sourcePath 不存在: " + sourcePath);
        }
        String resolvedFileName = StringUtils.hasText(fileName)
                ? fileName.trim()
                : sourcePath.getFileName().toString();
        String objectName = buildArtifactObjectName(folder, resolvedFileName);
        String resolvedContentType = resolveContentType(contentType, resolvedFileName);
        try (InputStream inputStream = Files.newInputStream(sourcePath)) {
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectName).stream(
                            inputStream, Files.size(sourcePath), -1)
                    .contentType(resolvedContentType)
                    .build());
        }
        log.info("产物上传成功：bucket={}, objectName={}, sourcePath={}", bucket, objectName, sourcePath);
        return new ArtifactUploadResult(
                bucket, objectName, resolvedFileName, toArtifactPath(objectName), resolvedContentType);
    }

    public void uploadObject(String objectName, byte[] content, String contentType) throws Exception {
        String resolvedObjectName = StringUtils.hasText(objectName) ? objectName.trim() : "";
        if (!StringUtils.hasText(resolvedObjectName)) {
            throw new IllegalArgumentException("objectName 不能为空");
        }
        byte[] safeContent = content == null ? new byte[0] : content;
        String resolvedFileName = extractFileNameFromObjectName(resolvedObjectName, "object");
        String resolvedContentType = resolveContentType(contentType, resolvedFileName);
        minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(resolvedObjectName).stream(
                        new ByteArrayInputStream(safeContent), safeContent.length, -1)
                .contentType(resolvedContentType)
                .build());
        log.info("对象上传成功：bucket={}, objectName={}, size={}", bucket, resolvedObjectName, safeContent.length);
    }

    public StoredFileDescriptor toChatUploadDescriptor(String id, String fileName, long size, String objectName) {
        return new StoredFileDescriptor(
                id,
                fileName,
                size,
                bucket,
                objectName,
                toChatUploadPath(objectName),
                buildArtifactDownloadUrl(objectName, fileName),
                resolveContentType(null, fileName));
    }

    public StoredFileDescriptor toKnowledgeDocumentDescriptor(
            Long docId, String fileName, long size, String objectName) {
        String id = docId == null ? null : String.valueOf(docId);
        return new StoredFileDescriptor(
                id,
                fileName,
                size,
                bucket,
                objectName,
                toMinioPath(objectName),
                buildArtifactDownloadUrl(objectName, fileName),
                resolveContentType(null, fileName));
    }

    public StoredFileDescriptor toArtifactDescriptor(ArtifactUploadResult uploadResult) {
        if (uploadResult == null) {
            return null;
        }
        return new StoredFileDescriptor(
                toArtifactShortId(uploadResult.objectName()),
                uploadResult.fileName(),
                null,
                uploadResult.bucket(),
                uploadResult.objectName(),
                uploadResult.path(),
                buildArtifactDownloadUrl(uploadResult.objectName(), uploadResult.fileName()),
                uploadResult.contentType());
    }

    private void putMultipartFile(String objectName, MultipartFile file) throws Exception {
        minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectName).stream(
                        file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
    }

    private String extractExtension(MultipartFile file) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }

    private String buildArtifactObjectName(String folder, String fileName) {
        String normalizedFolder = StringUtils.hasText(folder)
                ? folder.trim().replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "")
                : "artifacts";
        String normalizedFileName = StringUtils.hasText(fileName) ? fileName.trim() : "artifact";
        String extension = extractExtension(normalizedFileName);
        return normalizedFolder + "/" + UUID.randomUUID() + extension;
    }

    private String toChatUploadPath(String objectName) {
        return "chat-upload://" + objectName;
    }

    private String toMinioPath(String objectName) {
        return "minio://" + bucket + "/" + objectName;
    }

    private String toArtifactPath(String objectName) {
        return "artifact://" + bucket + "/" + objectName;
    }

    public String buildArtifactDownloadUrl(String objectName, String fileName) {
        String artifactId = toArtifactId(objectName);
        String encodedFileName =
                URLEncoder.encode(StringUtils.hasText(fileName) ? fileName.trim() : "download", StandardCharsets.UTF_8);
        return "/api/files/artifacts/" + artifactId + "/download?fileName=" + encodedFileName;
    }

    public String buildArtifactPreviewUrl(String objectName, String fileName) {
        String artifactId = toArtifactId(objectName);
        String encodedFileName =
                URLEncoder.encode(StringUtils.hasText(fileName) ? fileName.trim() : "preview", StandardCharsets.UTF_8);
        return "/api/files/artifacts/" + artifactId + "/preview?fileName=" + encodedFileName;
    }

    public String buildObjectPreviewUrl(String objectName) {
        return buildArtifactPreviewUrl(objectName, extractFileNameFromObjectName(objectName, "preview"));
    }

    public String buildMediaObjectName(
            String resourceType, Long resourceId, String mediaCategory, String fileNamePrefix, long version, String extension) {
        String normalizedResourceType = normalizePathSegment(resourceType, "resources");
        long safeResourceId = resourceId == null || resourceId <= 0 ? 0L : resourceId;
        String normalizedMediaCategory = normalizePathSegment(mediaCategory, "media");
        String normalizedFileNamePrefix = normalizePathSegment(fileNamePrefix, "file");
        String normalizedExtension = normalizeExtension(extension);
        return "media/" + normalizedResourceType + "/" + safeResourceId + "/" + normalizedMediaCategory + "/"
                + normalizedFileNamePrefix + "_" + safeResourceId + "_" + version + normalizedExtension;
    }

    public static String toArtifactId(String objectName) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectName.getBytes(StandardCharsets.UTF_8));
    }

    public static String toArtifactShortId(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return "";
        }
        String normalized = objectName.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String filePart = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = filePart.lastIndexOf('.');
        String baseName = dot > 0 ? filePart.substring(0, dot) : filePart;
        if (baseName.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            return baseName;
        }
        int dash = filePart.indexOf('-');
        if (dash > 0) {
            String candidate = filePart.substring(0, dash);
            if (candidate.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                return candidate;
            }
        }
        return toArtifactId(objectName);
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        String normalized = fileName.trim();
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(dot);
    }

    private String extractFileNameFromObjectName(String objectName, String fallback) {
        if (!StringUtils.hasText(objectName)) {
            return fallback;
        }
        String normalized = objectName.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash < 0) {
            return normalized;
        }
        if (slash == normalized.length() - 1) {
            return fallback;
        }
        return normalized.substring(slash + 1);
    }

    public String fromArtifactId(String artifactId) {
        byte[] decoded = Base64.getUrlDecoder().decode(artifactId);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private String normalizePathSegment(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase().replace('\\', '/');
        normalized = normalized.replaceAll("[^a-z0-9_-]+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String normalizeExtension(String extension) {
        if (!StringUtils.hasText(extension)) {
            return "";
        }
        String normalized = extension.trim().toLowerCase();
        if (!normalized.startsWith(".")) {
            normalized = "." + normalized;
        }
        return normalized.replaceAll("[^a-z0-9.]+", "");
    }

    private String resolveContentType(String contentType, String fileName) {
        if (StringUtils.hasText(contentType)) {
            return contentType.trim();
        }
        if (!StringUtils.hasText(fileName)) {
            return "application/octet-stream";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
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
            return "image/gif";
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
        return "application/octet-stream";
    }

    public record ArtifactUploadResult(
            String bucket, String objectName, String fileName, String path, String contentType) {}

    public record StoredFileDescriptor(
            String id,
            String fileName,
            Long size,
            String bucket,
            String objectName,
            String path,
            String downloadUrl,
            String contentType) {}
}
