package lingzhou.agent.backend.business.chat.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lingzhou.agent.backend.business.chat.domain.ConversationSession;
import lingzhou.agent.backend.business.chat.domain.RuntimeFileAsset;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionRequest;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.mapper.ConversationSessionMapper;
import lingzhou.agent.backend.business.chat.mapper.RuntimeFileAssetMapper;
import lingzhou.agent.backend.business.chat.util.UlidGenerator;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RuntimeFileAssetService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DELETED = "DELETED";
    private static final String LOCAL_PRESENT = "PRESENT";
    private static final String LOCAL_MISSING = "MISSING";
    private static final String LOCAL_DELETED = "DELETED";
    private static final String MINIO_UPLOADED = "UPLOADED";

    private final RuntimeFileAssetMapper runtimeFileAssetMapper;
    private final ConversationSessionMapper conversationSessionMapper;
    private final MinioService minioService;

    public RuntimeFileAssetService(
            RuntimeFileAssetMapper runtimeFileAssetMapper,
            ConversationSessionMapper conversationSessionMapper,
            MinioService minioService) {
        this.runtimeFileAssetMapper = runtimeFileAssetMapper;
        this.conversationSessionMapper = conversationSessionMapper;
        this.minioService = minioService;
    }

    @Transactional(rollbackFor = Exception.class)
    public RuntimeFileAsset recordUpload(
            MultipartFile file,
            Long userId,
            String fileCode,
            String objectName,
            UploadBinding binding,
            String bucketName,
            String contentType)
            throws IOException {
        RuntimeFileAsset asset = new RuntimeFileAsset();
        SessionBinding sessionBinding = resolveSessionBinding(userId, binding == null ? null : binding.sessionCode());
        String displayName = cleanFileName(file == null ? null : file.getOriginalFilename(), "file");
        String resolvedSessionCode = sessionBinding.sessionCode();

        asset.setFileCode(fileCode);
        asset.setUserId(safeUserId(userId));
        asset.setSessionId(sessionBinding.sessionId());
        asset.setSessionCode(resolvedSessionCode);
        asset.setOriginMessageId(binding == null ? null : binding.messageId());
        asset.setOriginEventId(binding == null ? null : binding.eventId());
        asset.setFileRole("UPLOAD");
        asset.setProducerType("USER_UPLOAD");
        asset.setStatus(STATUS_ACTIVE);
        asset.setDisplayName(displayName);
        asset.setStorageName(buildStorageName(displayName, resolvedSessionCode));
        asset.setExtension(extractExtension(displayName));
        asset.setContentType(normalizeText(contentType));
        asset.setSizeBytes(file == null ? null : file.getSize());
        asset.setSha256(file == null ? null : sha256(file.getInputStream()));
        asset.setLogicalRoot("UPLOADS");
        asset.setLogicalPath(asset.getStorageName());
        asset.setVirtualPath("/上传/" + displayName);
        asset.setLocalPath(null);
        asset.setLocalStatus(LOCAL_MISSING);
        asset.setBucket(normalizeText(bucketName));
        asset.setObjectName(normalizeText(objectName));
        asset.setMinioStatus(StringUtils.hasText(objectName) ? MINIO_UPLOADED : "PENDING");
        asset.setMetadataJson(buildUploadMetadata(binding, fileCode));
        runtimeFileAssetMapper.insert(asset);
        return asset;
    }

    @Transactional(rollbackFor = Exception.class)
    public RuntimeFileAsset recordArtifact(
            RuntimeExecutionRequest request,
            Path localFile,
            String logicalPath,
            MinioService.ArtifactUploadResult uploadResult,
            String contentType)
            throws IOException {
        RuntimeFileAsset asset = new RuntimeFileAsset();
        SessionBinding sessionBinding = resolveSessionBinding(
                request == null ? null : request.userId(), request == null ? null : request.sessionId());
        String displayName =
                cleanFileName(localFile == null ? null : localFile.getFileName().toString(), "artifact");
        String normalizedLogicalPath = normalizePath(logicalPath);

        asset.setFileCode(UlidGenerator.next());
        asset.setUserId(safeUserId(request == null ? null : request.userId()));
        asset.setSessionId(sessionBinding.sessionId());
        asset.setSessionCode(sessionBinding.sessionCode());
        asset.setRunId(request == null ? null : request.runId());
        asset.setOriginMessageId(resolveArtifactMessageId(request));
        asset.setOriginEventId(null);
        asset.setFileRole("ARTIFACT");
        asset.setProducerType("WRITE_ARTIFACT");
        asset.setStatus(STATUS_ACTIVE);
        asset.setDisplayName(displayName);
        asset.setStorageName(buildStorageName(displayName, sessionBinding.sessionCode()));
        asset.setExtension(extractExtension(displayName));
        asset.setContentType(normalizeText(contentType));
        asset.setSizeBytes(localFile == null ? null : Files.size(localFile));
        asset.setSha256(localFile == null ? null : sha256(localFile));
        asset.setLogicalRoot("OUTPUTS");
        asset.setLogicalPath(normalizedLogicalPath);
        asset.setVirtualPath("/产物/" + normalizedLogicalPath);
        asset.setLocalPath(
                localFile == null
                        ? null
                        : localFile.toAbsolutePath().normalize().toString());
        asset.setLocalStatus(localFile == null ? LOCAL_MISSING : LOCAL_PRESENT);
        asset.setBucket(uploadResult == null ? null : uploadResult.bucket());
        asset.setObjectName(uploadResult == null ? null : uploadResult.objectName());
        asset.setMinioStatus(uploadResult == null ? "PENDING" : MINIO_UPLOADED);
        asset.setMetadataJson(buildArtifactMetadata(request));
        runtimeFileAssetMapper.insert(asset);
        return asset;
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncTempFiles(
            RuntimeExecutionRequest request,
            Map<String, FileSnapshot> beforeSnapshot,
            Map<String, FileSnapshot> afterSnapshot)
            throws IOException {
        if (request == null || request.workspace() == null) {
            return;
        }
        Map<String, FileSnapshot> safeBefore = beforeSnapshot == null ? Map.of() : beforeSnapshot;
        Map<String, FileSnapshot> safeAfter = afterSnapshot == null ? Map.of() : afterSnapshot;

        LinkedHashSet<String> changedOrCreated = new LinkedHashSet<>();
        for (Map.Entry<String, FileSnapshot> entry : safeAfter.entrySet()) {
            FileSnapshot previous = safeBefore.get(entry.getKey());
            if (previous == null || !previous.sameAs(entry.getValue())) {
                changedOrCreated.add(entry.getKey());
            }
        }
        for (String localPath : changedOrCreated) {
            FileSnapshot snapshot = safeAfter.get(localPath);
            if (snapshot == null) {
                continue;
            }
            upsertTempFile(request, Path.of(localPath), snapshot);
        }

        for (String localPath : safeBefore.keySet()) {
            if (!safeAfter.containsKey(localPath)) {
                markTempFileDeleted(request, localPath);
            }
        }
    }

    public Map<String, FileSnapshot> snapshotDirectory(Path root) throws IOException {
        if (root == null || !Files.exists(root) || !Files.isDirectory(root)) {
            return Map.of();
        }
        Map<String, FileSnapshot> snapshot = new LinkedHashMap<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    Path normalized = path.toAbsolutePath().normalize();
                    snapshot.put(
                            normalized.toString(),
                            new FileSnapshot(
                                    Files.size(normalized),
                                    Files.getLastModifiedTime(normalized).toMillis()));
                } catch (IOException ignored) {
                    // ignore snapshot failure for a single temp file
                }
            });
        }
        return Map.copyOf(snapshot);
    }

    public IPage<RuntimeFileAssetItemView> listAssets(
            Long userId, String sessionCode, String fileRole, Integer pageNo, Integer pageSize) {
        long safePageNo = pageNo == null || pageNo <= 0 ? 1 : pageNo;
        long safePageSize = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        SessionBinding sessionBinding = resolveSessionBinding(userId, sessionCode);

        QueryWrapper<RuntimeFileAsset> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", safeUserId(userId));
        if (StringUtils.hasText(fileRole)) {
            wrapper.eq("file_role", fileRole.trim().toUpperCase());
            if (!"TEMP".equalsIgnoreCase(fileRole.trim())) {
                wrapper.isNull("deleted_at");
            }
        } else {
            wrapper.and(nested -> nested.isNull("deleted_at").or().eq("file_role", "TEMP"));
        }
        if (StringUtils.hasText(sessionCode)) {
            if (sessionBinding.sessionId() != null && sessionBinding.sessionId() > 0) {
                wrapper.eq("session_id", sessionBinding.sessionId());
            } else {
                wrapper.eq("session_code", sessionCode.trim());
            }
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");

        IPage<RuntimeFileAsset> page = runtimeFileAssetMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        Page<RuntimeFileAssetItemView> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<RuntimeFileAssetItemView> records = (page.getRecords() == null
                        ? List.<RuntimeFileAsset>of()
                        : page.getRecords())
                .stream().map(this::toItemView).toList();
        result.setRecords(records);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindUploadsToSession(Long userId, String sessionCode, List<String> fileCodes) {
        SessionBinding sessionBinding = resolveSessionBinding(userId, sessionCode);
        if (!StringUtils.hasText(sessionBinding.sessionCode()) || fileCodes == null || fileCodes.isEmpty()) {
            return;
        }
        List<RuntimeFileAsset> uploads =
                runtimeFileAssetMapper.selectOwnedActiveUploadsByFileCodes(safeUserId(userId), fileCodes);
        for (RuntimeFileAsset upload : uploads) {
            if (upload == null || StringUtils.hasText(upload.getSessionCode())) {
                continue;
            }
            RuntimeFileAsset update = new RuntimeFileAsset();
            update.setId(upload.getId());
            update.setSessionId(sessionBinding.sessionId());
            update.setSessionCode(sessionBinding.sessionCode());
            update.setStorageName(buildStorageName(upload.getDisplayName(), sessionBinding.sessionCode()));
            update.setUpdatedAt(new Date());
            runtimeFileAssetMapper.updateById(update);
        }
    }

    public RuntimeFileAssetBinaryView loadOwnedFileBinary(Long userId, String fileCode, boolean preview)
            throws IOException {
        RuntimeFileAsset asset = runtimeFileAssetMapper.selectOwnedByFileCode(safeUserId(userId), fileCode);
        if (asset == null) {
            return null;
        }
        String fileName = cleanFileName(asset.getDisplayName(), "file");
        String contentType = detectContentTypeForAsset(asset, fileName);
        if (preview && !isPreviewableTextFile(fileName, contentType)) {
            throw new IllegalArgumentException("当前文件类型暂不支持预览");
        }
        byte[] content = readAssetBytes(asset);
        return new RuntimeFileAssetBinaryView(
                asset.getFileCode(), fileName, contentType, content, preview && isInlinePreview(contentType));
    }

    public record UploadBinding(String sessionCode, Long messageId, Long eventId) {}

    @Transactional(rollbackFor = Exception.class)
    public void syncWorkspaceFileWrite(RuntimeExecutionRequest request, RuntimeExecutionResult result)
            throws IOException {
        if (request == null || result == null || !result.success() || result.data() == null) {
            return;
        }
        String logicalPath = normalizeText(String.valueOf(result.data().getOrDefault("path", "")));
        String resolvedPath = normalizeText(String.valueOf(result.data().getOrDefault("resolvedPath", "")));
        if (!StringUtils.hasText(logicalPath) || !StringUtils.hasText(resolvedPath)) {
            return;
        }
        String normalizedLogicalPath = logicalPath.replace('\\', '/');
        if (!normalizedLogicalPath.startsWith("/workspace/")) {
            return;
        }
        Path localFile = Path.of(resolvedPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(localFile)) {
            return;
        }
        FileSnapshot snapshot = new FileSnapshot(
                Files.size(localFile), Files.getLastModifiedTime(localFile).toMillis());
        upsertManagedFile(request, localFile, snapshot, "TEMP", "FILE_WRITE", "WORKSPACE", normalizedLogicalPath);
    }

    private void upsertTempFile(RuntimeExecutionRequest request, Path localFile, FileSnapshot snapshot)
            throws IOException {
        upsertManagedFile(request, localFile, snapshot, "TEMP", "SYSTEM", "TEMP", null);
    }

    private void upsertManagedFile(
            RuntimeExecutionRequest request,
            Path localFile,
            FileSnapshot snapshot,
            String fileRole,
            String producerType,
            String logicalRoot,
            String explicitLogicalPath)
            throws IOException {
        if (request == null || localFile == null || snapshot == null || !Files.isRegularFile(localFile)) {
            return;
        }
        SessionBinding sessionBinding = resolveSessionBinding(request.userId(), request.sessionId());
        Long userId = safeUserId(request.userId());
        String normalizedLocalPath = localFile.toAbsolutePath().normalize().toString();
        RuntimeFileAsset existing = runtimeFileAssetMapper.selectActiveByLocalPath(
                userId, sessionBinding.sessionId(), fileRole, normalizedLocalPath);
        String contentHash = sha256(localFile);
        if (existing != null
                && contentHash.equals(existing.getSha256())
                && STATUS_ACTIVE.equals(existing.getStatus())) {
            return;
        }

        String fileName = cleanFileName(
                localFile.getFileName() == null ? null : localFile.getFileName().toString(), "temp");
        String logicalPath = resolveManagedLogicalPath(request, localFile, logicalRoot, explicitLogicalPath);
        String contentType = detectContentType(localFile, fileName);
        String previousObjectName = existing == null ? null : existing.getObjectName();
        MinioService.ArtifactUploadResult uploadResult = null;
        try {
            uploadResult = minioService.uploadArtifact(
                    localFile, buildTempObjectPrefix(sessionBinding, request), fileName, contentType);
            if (existing == null) {
                RuntimeFileAsset asset = new RuntimeFileAsset();
                asset.setFileCode(UlidGenerator.next());
                asset.setUserId(userId);
                asset.setSessionId(sessionBinding.sessionId());
                asset.setSessionCode(sessionBinding.sessionCode());
                asset.setRunId(request.runId());
                asset.setOriginMessageId(resolveArtifactMessageId(request));
                asset.setOriginEventId(null);
                asset.setFileRole(fileRole);
                asset.setProducerType(producerType);
                asset.setStatus(STATUS_ACTIVE);
                asset.setDisplayName(fileName);
                asset.setStorageName(buildStorageName(fileName, sessionBinding.sessionCode()));
                asset.setExtension(extractExtension(fileName));
                asset.setContentType(contentType);
                asset.setSizeBytes(snapshot.sizeBytes());
                asset.setSha256(contentHash);
                asset.setLogicalRoot(logicalRoot);
                asset.setLogicalPath(logicalPath);
                asset.setVirtualPath(buildVirtualPath(fileRole, logicalPath));
                asset.setLocalPath(normalizedLocalPath);
                asset.setLocalStatus(LOCAL_PRESENT);
                asset.setBucket(uploadResult.bucket());
                asset.setObjectName(uploadResult.objectName());
                asset.setMinioStatus(MINIO_UPLOADED);
                asset.setMetadataJson(buildTempMetadata(request));
                runtimeFileAssetMapper.insert(asset);
            } else {
                RuntimeFileAsset update = new RuntimeFileAsset();
                update.setId(existing.getId());
                update.setSessionId(sessionBinding.sessionId());
                update.setSessionCode(sessionBinding.sessionCode());
                update.setRunId(request.runId());
                update.setOriginMessageId(resolveArtifactMessageId(request));
                update.setStatus(STATUS_ACTIVE);
                update.setDisplayName(fileName);
                update.setStorageName(buildStorageName(fileName, sessionBinding.sessionCode()));
                update.setExtension(extractExtension(fileName));
                update.setContentType(contentType);
                update.setSizeBytes(snapshot.sizeBytes());
                update.setSha256(contentHash);
                update.setLogicalRoot(logicalRoot);
                update.setLogicalPath(logicalPath);
                update.setVirtualPath(buildVirtualPath(fileRole, logicalPath));
                update.setLocalPath(normalizedLocalPath);
                update.setLocalStatus(LOCAL_PRESENT);
                update.setBucket(uploadResult.bucket());
                update.setObjectName(uploadResult.objectName());
                update.setMinioStatus(MINIO_UPLOADED);
                update.setMetadataJson(buildTempMetadata(request));
                update.setDeletedAt(null);
                runtimeFileAssetMapper.updateById(update);
            }
        } catch (Exception ex) {
            cleanupUploadedObject(uploadResult == null ? null : uploadResult.objectName());
            throw new IOException("同步 TEMP 文件失败: " + localFile + ", " + ex.getMessage(), ex);
        }
        if (StringUtils.hasText(previousObjectName)
                && uploadResult != null
                && StringUtils.hasText(uploadResult.objectName())
                && !previousObjectName.equals(uploadResult.objectName())) {
            cleanupUploadedObject(previousObjectName);
        }
    }

    private String resolveManagedLogicalPath(
            RuntimeExecutionRequest request, Path localFile, String logicalRoot, String explicitLogicalPath) {
        if (StringUtils.hasText(explicitLogicalPath)) {
            String normalized = explicitLogicalPath.trim().replace('\\', '/');
            if ("WORKSPACE".equalsIgnoreCase(logicalRoot) && normalized.startsWith("/workspace/")) {
                normalized = normalized.substring("/workspace/".length());
            }
            return normalizePath(normalized);
        }
        if ("TEMP".equalsIgnoreCase(logicalRoot) && request != null && request.workspace() != null) {
            return buildRelativePath(Path.of(request.workspace().tempRoot()), localFile);
        }
        if ("WORKSPACE".equalsIgnoreCase(logicalRoot) && request != null && request.workspace() != null) {
            return buildRelativePath(Path.of(request.workspace().workspaceRoot()), localFile);
        }
        return cleanFileName(
                localFile.getFileName() == null ? null : localFile.getFileName().toString(), "temp");
    }

    private String buildVirtualPath(String fileRole, String logicalPath) {
        String prefix =
                switch (StringUtils.hasText(fileRole) ? fileRole.trim().toUpperCase() : "") {
                    case "UPLOAD" -> "/上传/";
                    case "ARTIFACT" -> "/产物/";
                    case "TEMP" -> "/临时/";
                    default -> "/文件/";
                };
        return prefix + normalizePath(logicalPath);
    }

    private void markTempFileDeleted(RuntimeExecutionRequest request, String localPath) {
        if (request == null || !StringUtils.hasText(localPath)) {
            return;
        }
        SessionBinding sessionBinding = resolveSessionBinding(request.userId(), request.sessionId());
        RuntimeFileAsset existing = runtimeFileAssetMapper.selectActiveByLocalPath(
                safeUserId(request.userId()), sessionBinding.sessionId(), "TEMP", localPath);
        if (existing == null || existing.getId() == null) {
            return;
        }
        RuntimeFileAsset update = new RuntimeFileAsset();
        update.setId(existing.getId());
        update.setStatus(STATUS_ACTIVE);
        update.setLocalStatus(LOCAL_DELETED);
        update.setDeletedAt(null);
        runtimeFileAssetMapper.updateById(update);
    }

    private SessionBinding resolveSessionBinding(Long userId, String sessionCode) {
        String normalizedSessionCode = normalizeText(sessionCode);
        if (!StringUtils.hasText(normalizedSessionCode)) {
            return new SessionBinding(null, null);
        }
        ConversationSession session;
        Long safeUserId = safeUserId(userId);
        if (safeUserId > 0) {
            session = conversationSessionMapper.selectBySessionCodeGlobal(normalizedSessionCode);
            if (session != null && session.getCreateUserId() != null && !safeUserId.equals(session.getCreateUserId())) {
                session = null;
            }
        } else {
            session = conversationSessionMapper.selectBySessionCodeGlobal(normalizedSessionCode);
        }
        if (session == null) {
            return new SessionBinding(null, normalizedSessionCode);
        }
        return new SessionBinding(session.getId(), session.getSessionCode());
    }

    private Long resolveArtifactMessageId(RuntimeExecutionRequest request) {
        if (request == null) {
            return null;
        }
        if (request.assistantMessageId() != null && request.assistantMessageId() > 0) {
            return request.assistantMessageId();
        }
        if (request.requestMessageId() != null && request.requestMessageId() > 0) {
            return request.requestMessageId();
        }
        return null;
    }

    private String buildUploadMetadata(UploadBinding binding, String fileCode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (binding != null && StringUtils.hasText(binding.sessionCode())) {
            metadata.put("requestedSessionCode", binding.sessionCode().trim());
        }
        if (binding != null && binding.messageId() != null) {
            metadata.put("requestedMessageId", binding.messageId());
        }
        if (binding != null && binding.eventId() != null) {
            metadata.put("requestedEventId", binding.eventId());
        }
        if (StringUtils.hasText(fileCode)) {
            metadata.put("fileCode", fileCode);
        }
        return metadata.isEmpty() ? null : JSON.toJSONString(metadata);
    }

    private String buildArtifactMetadata(RuntimeExecutionRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.runId() != null && request.runId() > 0) {
            metadata.put("runId", request.runId());
        }
        if (request.requestMessageId() != null) {
            metadata.put("requestMessageId", request.requestMessageId());
        }
        if (request.assistantMessageId() != null) {
            metadata.put("assistantMessageId", request.assistantMessageId());
        }
        if (request.payload() != null) {
            Object folder = request.payload().get("folder");
            Object sourcePath = request.payload().get("sourcePath");
            if (folder != null) {
                metadata.put("folder", String.valueOf(folder));
            }
            if (sourcePath != null) {
                metadata.put("sourcePath", String.valueOf(sourcePath));
            }
        }
        return metadata.isEmpty() ? null : JSON.toJSONString(metadata);
    }

    private String buildTempMetadata(RuntimeExecutionRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("action", request.action() == null ? "" : request.action().name());
        if (request.runId() != null && request.runId() > 0) {
            metadata.put("runId", request.runId());
        }
        if (request.requestMessageId() != null) {
            metadata.put("requestMessageId", request.requestMessageId());
        }
        if (request.assistantMessageId() != null) {
            metadata.put("assistantMessageId", request.assistantMessageId());
        }
        return JSON.toJSONString(metadata);
    }

    private String buildTempObjectPrefix(SessionBinding sessionBinding, RuntimeExecutionRequest request) {
        String sessionSegment = StringUtils.hasText(sessionBinding.sessionCode())
                ? sessionBinding.sessionCode()
                : (request == null ? "" : normalizeText(request.sessionId()));
        if (!StringUtils.hasText(sessionSegment)) {
            sessionSegment = "default";
        }
        return "runtime/temp/" + sessionSegment;
    }

    private String buildStorageName(String fileName, String sessionCode) {
        String normalizedFileName = cleanFileName(fileName, "file");
        int dot = normalizedFileName.lastIndexOf('.');
        String baseName = dot > 0 ? normalizedFileName.substring(0, dot) : normalizedFileName;
        String extension = dot > 0 ? normalizedFileName.substring(dot) : "";
        String suffix = StringUtils.hasText(sessionCode) ? "__" + sessionCode.trim() : "";
        return baseName + suffix + extension;
    }

    private String buildRelativePath(Path root, Path file) {
        if (root == null || file == null) {
            return "temp";
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            return cleanFileName(
                    normalizedFile.getFileName() == null
                            ? null
                            : normalizedFile.getFileName().toString(),
                    "temp");
        }
        Path relativePath = normalizedRoot.relativize(normalizedFile);
        return relativePath.toString().replace('\\', '/');
    }

    private String cleanFileName(String fileName, String fallback) {
        String normalized = StringUtils.cleanPath(StringUtils.hasText(fileName) ? fileName.trim() : fallback);
        String leaf = Path.of(normalized).getFileName().toString();
        return StringUtils.hasText(leaf) ? leaf : fallback;
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot).toLowerCase() : null;
    }

    private String detectContentType(Path file, String fileName) {
        if (file != null) {
            try {
                String detected = Files.probeContentType(file);
                if (StringUtils.hasText(detected)) {
                    return detected;
                }
            } catch (IOException ignored) {
                // ignore
            }
        }
        String extension = extractExtension(fileName);
        if (".json".equals(extension)) {
            return "application/json";
        }
        if (".html".equals(extension) || ".htm".equals(extension)) {
            return "text/html";
        }
        if (".md".equals(extension)
                || ".txt".equals(extension)
                || ".log".equals(extension)
                || ".csv".equals(extension)) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private String normalizePath(String path) {
        String normalized = StringUtils.hasText(path) ? path.trim().replace('\\', '/') : "";
        normalized = normalized.replaceAll("^/+", "").replaceAll("/+$", "");
        return StringUtils.hasText(normalized) ? normalized : "artifact";
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long safeUserId(Long userId) {
        return userId == null || userId <= 0 ? 0L : userId;
    }

    private String sha256(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return sha256(inputStream);
        }
    }

    private String sha256(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IOException("计算文件哈希失败: " + ex.getMessage(), ex);
        }
    }

    private void cleanupUploadedObject(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return;
        }
        try {
            minioService.deleteFile(objectName);
        } catch (Exception ignored) {
            // ignore cleanup failure
        }
    }

    private RuntimeFileAssetItemView toItemView(RuntimeFileAsset asset) {
        return new RuntimeFileAssetItemView(
                asset.getFileCode(),
                asset.getFileRole(),
                asset.getStatus(),
                asset.getDisplayName(),
                asset.getVirtualPath(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getOriginMessageId(),
                asset.getOriginEventId(),
                asset.getLocalStatus(),
                asset.getMinioStatus(),
                asset.getObjectName(),
                asset.getCreatedAt(),
                asset.getUpdatedAt());
    }

    private byte[] readAssetBytes(RuntimeFileAsset asset) throws IOException {
        if (asset == null) {
            return new byte[0];
        }
        String localPath = normalizeText(asset.getLocalPath());
        if (StringUtils.hasText(localPath)) {
            Path localFile = Path.of(localPath).toAbsolutePath().normalize();
            if (Files.exists(localFile) && Files.isRegularFile(localFile)) {
                return Files.readAllBytes(localFile);
            }
        }
        String objectName = normalizeText(asset.getObjectName());
        if (!StringUtils.hasText(objectName)) {
            throw new IOException("文件内容不存在");
        }
        try (InputStream inputStream = minioService.getFile(objectName)) {
            return inputStream.readAllBytes();
        } catch (Exception ex) {
            throw new IOException("读取文件失败: " + ex.getMessage(), ex);
        }
    }

    private String detectContentTypeForAsset(RuntimeFileAsset asset, String fileName) {
        String contentType = normalizeText(asset == null ? null : asset.getContentType());
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        return detectContentType(null, fileName);
    }

    private boolean isPreviewableTextFile(String fileName, String contentType) {
        String normalizedFileName =
                StringUtils.hasText(fileName) ? fileName.trim().toLowerCase() : "";
        String normalizedContentType =
                StringUtils.hasText(contentType) ? contentType.trim().toLowerCase() : "";
        return normalizedContentType.startsWith("text/")
                || normalizedContentType.equals("application/json")
                || normalizedFileName.endsWith(".md")
                || normalizedFileName.endsWith(".txt")
                || normalizedFileName.endsWith(".html")
                || normalizedFileName.endsWith(".htm")
                || normalizedFileName.endsWith(".json")
                || normalizedFileName.endsWith(".log")
                || normalizedFileName.endsWith(".csv");
    }

    private boolean isInlinePreview(String contentType) {
        String normalized =
                StringUtils.hasText(contentType) ? contentType.trim().toLowerCase() : "";
        return normalized.startsWith("text/") || normalized.equals("application/json");
    }

    private record SessionBinding(Long sessionId, String sessionCode) {}

    public record FileSnapshot(Long sizeBytes, Long lastModifiedMillis) {
        boolean sameAs(FileSnapshot other) {
            return other != null
                    && Objects.equals(sizeBytes, other.sizeBytes)
                    && Objects.equals(lastModifiedMillis, other.lastModifiedMillis);
        }
    }

    public record RuntimeFileAssetItemView(
            String fileCode,
            String fileRole,
            String status,
            String displayName,
            String virtualPath,
            String contentType,
            Long sizeBytes,
            Long originMessageId,
            Long originEventId,
            String localStatus,
            String minioStatus,
            String objectName,
            Date createdAt,
            Date updatedAt) {}

    public record RuntimeFileAssetBinaryView(
            String fileCode, String fileName, String contentType, byte[] content, boolean inlinePreview) {}
}
