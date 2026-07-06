package lingzhou.agent.backend.business.chat.execution.artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lingzhou.agent.backend.business.chat.domain.RuntimeFileAsset;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionRequest;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.execution.nativefs.LogicalPathResolver;
import lingzhou.agent.backend.business.chat.execution.nativefs.PathJail;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.business.chat.service.RuntimeFileAssetService;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeArtifactService {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeArtifactService.class);

    private final MinioService minioService;
    private final LogicalPathResolver logicalPathResolver;
    private final RuntimeFileAssetService runtimeFileAssetService;

    public RuntimeArtifactService(
            MinioService minioService,
            LogicalPathResolver logicalPathResolver,
            RuntimeFileAssetService runtimeFileAssetService) {
        this.minioService = minioService;
        this.logicalPathResolver = logicalPathResolver;
        this.runtimeFileAssetService = runtimeFileAssetService;
    }

    public RuntimeExecutionResult writeArtifact(PathJail jail, RuntimeExecutionRequest request) {
        String folder = stringValue(request.payload(), "folder");
        String fileName = stringValue(request.payload(), "fileName");
        String content = stringValue(request.payload(), "content");
        String sourcePath = stringValue(request.payload(), "sourcePath");
        String contentType = stringValue(request.payload(), "contentType");
        MinioService.ArtifactUploadResult uploadResult = null;
        try {
            Path outputFile;
            String prefix = resolveArtifactPrefix(request, folder);
            if (StringUtils.hasText(sourcePath)) {
                String normalizedLogicalPath = logicalPathResolver.normalizeLogicalPath(sourcePath);
                Path hostPath =
                        jail.assertReadable(logicalPathResolver.resolve(request.workspace(), normalizedLogicalPath));
                String resolvedFileName = resolveOutputFileName(fileName, hostPath);
                outputFile = copySourceToOutputFile(jail, request, hostPath, resolvedFileName);
                uploadResult = minioService.uploadArtifact(outputFile, prefix, resolvedFileName, contentType);
            } else {
                outputFile = materializeContentToOutputFile(jail, request, fileName, content);
                uploadResult = minioService.uploadArtifact(outputFile, prefix, fileName, contentType);
            }
            RuntimeFileAsset asset = runtimeFileAssetService.recordArtifact(
                    request, outputFile, toLogicalOutputPath(request, outputFile), uploadResult, contentType);
            MinioService.StoredFileDescriptor file = minioService.toArtifactDescriptor(uploadResult);
            boolean previewable =
                    isHtmlArtifact(file == null ? null : file.fileName(), file == null ? null : file.contentType());
            RuntimeArtifactDescriptor descriptor = new RuntimeArtifactDescriptor(
                    file.id(),
                    file.bucket(),
                    file.objectName(),
                    file.fileName(),
                    file.path(),
                    file.downloadUrl(),
                    minioService.buildArtifactPreviewUrl(file.objectName(), file.fileName()),
                    file.contentType(),
                    previewable);
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("id", descriptor.id());
            artifact.put("bucket", descriptor.bucket());
            artifact.put("objectName", descriptor.objectName());
            artifact.put("fileName", descriptor.fileName());
            artifact.put("path", descriptor.path());
            artifact.put("downloadUrl", descriptor.downloadUrl());
            artifact.put("previewUrl", descriptor.previewUrl());
            artifact.put("contentType", descriptor.contentType());
            artifact.put("previewable", descriptor.previewable());
            artifact.put("assetCode", asset.getFileCode());
            return RuntimeExecutionResult.success(
                    request.action(),
                    "Artifact created:\n- fileName: " + descriptor.fileName() + "\n- path: " + descriptor.path()
                            + "\n- downloadUrl: " + descriptor.downloadUrl(),
                    Map.of("artifact", artifact));
        } catch (Exception ex) {
            cleanupUploadedArtifact(uploadResult);
            logger.error(
                    "产物登记失败：sessionId={}, requestMessageId={}, assistantMessageId={}, error={}",
                    request == null ? null : request.sessionId(),
                    request == null ? null : request.requestMessageId(),
                    request == null ? null : request.assistantMessageId(),
                    ex.getMessage(),
                    ex);
            return RuntimeExecutionResult.failure(request.action(), "WRITE_ARTIFACT_FAILED", ex.getMessage());
        }
    }

    private void cleanupUploadedArtifact(MinioService.ArtifactUploadResult uploadResult) {
        if (uploadResult == null || !StringUtils.hasText(uploadResult.objectName())) {
            return;
        }
        try {
            minioService.deleteFile(uploadResult.objectName());
        } catch (Exception ex) {
            logger.warn("清理失败的产物对象失败：objectName={}, error={}", uploadResult.objectName(), ex.getMessage(), ex);
        }
    }

    private Path copySourceToOutputFile(
            PathJail jail, RuntimeExecutionRequest request, Path sourcePath, String fileName) throws IOException {
        if (sourcePath == null || !Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException("artifact sourcePath 不存在: " + sourcePath);
        }
        Path outputPath = resolveWritableOutputPath(jail, request, resolveOutputFileName(fileName, sourcePath));
        if (Files.exists(outputPath) && Files.isSameFile(sourcePath, outputPath)) {
            return outputPath;
        }
        Files.copy(sourcePath, outputPath, StandardCopyOption.REPLACE_EXISTING);
        return outputPath;
    }

    private Path materializeContentToOutputFile(
            PathJail jail, RuntimeExecutionRequest request, String fileName, String content) throws IOException {
        String resolvedFileName =
                StringUtils.hasText(fileName) ? fileName.trim() : "artifact-" + UUID.randomUUID() + ".txt";
        Path outputPath = resolveWritableOutputPath(jail, request, resolvedFileName);
        Files.writeString(outputPath, content == null ? "" : content, StandardCharsets.UTF_8);
        return outputPath;
    }

    private Path resolveWritableOutputPath(PathJail jail, RuntimeExecutionRequest request, String fileName)
            throws IOException {
        Path relativePath = normalizeRelativeOutputPath(fileName);
        Path outputPath =
                Path.of(request.workspace().outputsRoot()).resolve(relativePath).normalize();
        Path parent = outputPath.getParent();
        if (parent != null) {
            jail.assertWritable(parent);
            Files.createDirectories(parent);
        }
        jail.assertWritable(outputPath);
        return outputPath;
    }

    private Path normalizeRelativeOutputPath(String fileName) {
        String normalizedFileName = StringUtils.hasText(fileName)
                ? fileName.trim().replace('\\', '/')
                : "artifact-" + UUID.randomUUID() + ".txt";
        Path relativePath = Path.of(normalizedFileName).normalize();
        if (relativePath.isAbsolute() || relativePath.toString().isBlank() || relativePath.startsWith("..")) {
            throw new IllegalArgumentException("artifact fileName 必须是 outputs 下的相对文件名: " + fileName);
        }
        return relativePath;
    }

    private String resolveOutputFileName(String fileName, Path sourcePath) {
        if (StringUtils.hasText(fileName)) {
            return fileName.trim();
        }
        Path sourceFileName = sourcePath == null ? null : sourcePath.getFileName();
        if (sourceFileName != null && StringUtils.hasText(sourceFileName.toString())) {
            return sourceFileName.toString();
        }
        return "artifact-" + UUID.randomUUID();
    }

    private String resolveArtifactPrefix(RuntimeExecutionRequest request, String folder) {
        String normalizedFolder = StringUtils.hasText(folder) ? sanitizeFolder(folder) : "default";
        if ((request.scopeType() == LingzRuntimeScopeType.SKILL_STUDIO_PROJECT
                        || request.scopeType() == LingzRuntimeScopeType.SKILL_STUDIO_PROJECT_PREVIEW)
                && request.scopeId() != null) {
            return "skillstudio/artifacts/" + request.scopeId() + "/" + normalizedFolder;
        }
        if (request.scopeType() == LingzRuntimeScopeType.CHANNEL && request.scopeId() != null) {
            return "apps/artifacts/" + request.scopeId() + "/" + request.sessionId() + "/" + normalizedFolder;
        }
        return "runtime/artifacts/" + request.sessionId() + "/" + normalizedFolder;
    }

    private String sanitizeFolder(String folder) {
        return folder.trim().replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String stringValue(Map<String, Object> payload, String key) {
        if (payload == null || key == null || key.isBlank()) {
            return "";
        }
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isHtmlArtifact(String fileName, String contentType) {
        String normalizedContentType =
                StringUtils.hasText(contentType) ? contentType.trim().toLowerCase() : "";
        if (normalizedContentType.startsWith("text/html")) {
            return true;
        }
        String normalizedFileName =
                StringUtils.hasText(fileName) ? fileName.trim().toLowerCase() : "";
        return normalizedFileName.endsWith(".html") || normalizedFileName.endsWith(".htm");
    }

    private String toLogicalOutputPath(RuntimeExecutionRequest request, Path outputFile) {
        if (request == null || request.workspace() == null || outputFile == null) {
            return "artifact";
        }
        Path outputsRoot =
                Path.of(request.workspace().outputsRoot()).toAbsolutePath().normalize();
        Path normalizedOutputFile = outputFile.toAbsolutePath().normalize();
        Path relativePath = outputsRoot.relativize(normalizedOutputFile);
        return relativePath.toString().replace('\\', '/');
    }
}
