package lingzhou.agent.backend.business.chat.execution.nativefs;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionRequest;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NativeFileExecutor {

    private final LogicalPathResolver logicalPathResolver;
    private final RuntimeExecutionProperties properties;

    public NativeFileExecutor(LogicalPathResolver logicalPathResolver, RuntimeExecutionProperties properties) {
        this.logicalPathResolver = logicalPathResolver;
        this.properties = properties;
    }

    public RuntimeExecutionResult execute(PathJail jail, RuntimeExecutionRequest request) {
        return switch (request.action()) {
            case FILE_READ -> readFile(jail, request);
            case FILE_WRITE -> writeFile(jail, request);
            case LIST_DIR -> listDir(jail, request);
            case STAT -> stat(jail, request);
            case SEARCH, WRITE_ARTIFACT, BASH, RUN_PYTHON -> RuntimeExecutionResult.failure(
                    request.action(),
                    "UNSUPPORTED_NATIVE_FILE_ACTION",
                    "当前 action 不属于 NativeFileExecutor: " + request.action());
        };
    }

    private RuntimeExecutionResult readFile(PathJail jail, RuntimeExecutionRequest request) {
        String logicalPath = stringValue(request.payload(), "path");
        try {
            String normalizedLogicalPath = logicalPathResolver.normalizeLogicalPath(logicalPath);
            Path hostPath =
                    jail.assertReadable(logicalPathResolver.resolve(request.workspace(), normalizedLogicalPath));
            if (!Files.exists(hostPath) || !Files.isRegularFile(hostPath)) {
                return RuntimeExecutionResult.failure(
                        request.action(), "FILE_NOT_FOUND", "文件不存在: " + normalizedLogicalPath);
            }
            if (isBinaryLikeFile(hostPath)) {
                return RuntimeExecutionResult.failure(
                        request.action(),
                        "FILE_READ_BINARY_UNSUPPORTED",
                        "file_read 仅支持读取 UTF-8 文本文件，当前文件看起来是二进制文件: " + normalizedLogicalPath
                                + "。对于 .docx/.pdf/.xlsx 等附件，请优先使用系统预解析内容，或通过 run_python / 专用解析脚本处理。");
            }
            String content = Files.readString(hostPath, StandardCharsets.UTF_8);
            if (content.length() > properties.getMaxReadFileChars()) {
                content = content.substring(0, properties.getMaxReadFileChars()) + "\n[truncated]";
            }
            return RuntimeExecutionResult.success(
                    request.action(),
                    content,
                    mapOf("path", normalizedLogicalPath, "resolvedPath", hostPath.toString(), "content", content));
        } catch (MalformedInputException ex) {
            return RuntimeExecutionResult.failure(
                    request.action(),
                    "FILE_READ_BINARY_UNSUPPORTED",
                    "file_read 仅支持读取 UTF-8 文本文件，当前文件无法按文本解码: " + logicalPath
                            + "。对于二进制附件，请优先使用系统预解析内容，或通过 run_python / 专用解析脚本处理。");
        } catch (IllegalArgumentException | IOException | SandboxViolationException ex) {
            return RuntimeExecutionResult.failure(request.action(), "FILE_READ_FAILED", ex.getMessage());
        }
    }

    private RuntimeExecutionResult writeFile(PathJail jail, RuntimeExecutionRequest request) {
        String logicalPath = stringValue(request.payload(), "path");
        String content = stringValue(request.payload(), "content");
        try {
            String normalizedLogicalPath = logicalPathResolver.normalizeLogicalPath(logicalPath);
            String pythonScriptRisk = validatePythonScriptWrite(normalizedLogicalPath, content);
            if (StringUtils.hasText(pythonScriptRisk)) {
                return RuntimeExecutionResult.failure(request.action(), "FILE_WRITE_PYTHON_BLOCKED", pythonScriptRisk);
            }
            Path hostPath = logicalPathResolver.resolve(request.workspace(), normalizedLogicalPath);
            Path parent = hostPath.getParent();
            if (parent != null) {
                jail.assertWritable(parent);
                Files.createDirectories(parent);
            }
            jail.assertWritable(hostPath);
            Files.writeString(hostPath, content == null ? "" : content, StandardCharsets.UTF_8);
            return RuntimeExecutionResult.success(
                    request.action(),
                    "{\"success\":true,\"path\":\"" + escapeJson(normalizedLogicalPath) + "\"}",
                    mapOf(
                            "path",
                            normalizedLogicalPath,
                            "resolvedPath",
                            hostPath.toString(),
                            "bytes",
                            (content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length)));
        } catch (IllegalArgumentException | IOException | SandboxViolationException ex) {
            return RuntimeExecutionResult.failure(request.action(), "FILE_WRITE_FAILED", ex.getMessage());
        }
    }

    private RuntimeExecutionResult listDir(PathJail jail, RuntimeExecutionRequest request) {
        String logicalPath = stringValue(request.payload(), "path");
        try {
            String normalizedLogicalPath = logicalPathResolver.normalizeLogicalPath(logicalPath);
            Path hostPath =
                    jail.assertReadable(logicalPathResolver.resolve(request.workspace(), normalizedLogicalPath));
            if (!Files.exists(hostPath) || !Files.isDirectory(hostPath)) {
                return RuntimeExecutionResult.failure(
                        request.action(), "DIRECTORY_NOT_FOUND", "目录不存在: " + normalizedLogicalPath);
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            try (var stream = Files.list(hostPath)) {
                stream.sorted().forEach(path -> entries.add(toEntry(normalizedLogicalPath, path)));
            }
            return RuntimeExecutionResult.success(
                    request.action(),
                    entries.stream()
                            .map(entry -> entry.get("type") + " " + entry.get("path"))
                            .reduce((left, right) -> left + "\n" + right)
                            .orElse(""),
                    mapOf("path", normalizedLogicalPath, "entries", entries));
        } catch (IllegalArgumentException | IOException | SandboxViolationException ex) {
            return RuntimeExecutionResult.failure(request.action(), "LIST_DIR_FAILED", ex.getMessage());
        }
    }

    private RuntimeExecutionResult stat(PathJail jail, RuntimeExecutionRequest request) {
        String logicalPath = stringValue(request.payload(), "path");
        try {
            String normalizedLogicalPath = logicalPathResolver.normalizeLogicalPath(logicalPath);
            Path hostPath =
                    jail.assertReadable(logicalPathResolver.resolve(request.workspace(), normalizedLogicalPath));
            if (!Files.exists(hostPath)) {
                return RuntimeExecutionResult.success(
                        request.action(), "not found", mapOf("path", normalizedLogicalPath, "exists", false));
            }
            FileTime lastModifiedTime = Files.getLastModifiedTime(hostPath);
            boolean directory = Files.isDirectory(hostPath);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizedLogicalPath);
            data.put("resolvedPath", hostPath.toString());
            data.put("exists", true);
            data.put("type", directory ? "directory" : "file");
            data.put("size", directory ? null : Files.size(hostPath));
            data.put("lastModifiedAt", DateTimeFormatter.ISO_INSTANT.format(lastModifiedTime.toInstant()));
            return RuntimeExecutionResult.success(request.action(), data.toString(), data);
        } catch (IllegalArgumentException | IOException | SandboxViolationException ex) {
            return RuntimeExecutionResult.failure(request.action(), "STAT_FAILED", ex.getMessage());
        }
    }

    private Map<String, Object> toEntry(String parentLogicalPath, Path path) {
        String base = "/".equals(parentLogicalPath) ? "" : parentLogicalPath;
        String name = path.getFileName() == null
                ? path.toString()
                : path.getFileName().toString();
        return mapOf(
                "name",
                name,
                "path",
                base.endsWith("/") ? base + name : base + "/" + name,
                "type",
                Files.isDirectory(path) ? "directory" : "file",
                "size",
                safeSize(path));
    }

    private Long safeSize(Path path) {
        try {
            return Files.isDirectory(path) ? null : Files.size(path);
        } catch (IOException ex) {
            return null;
        }
    }

    private boolean isBinaryLikeFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".doc")
                || fileName.endsWith(".docx")
                || fileName.endsWith(".pdf")
                || fileName.endsWith(".xls")
                || fileName.endsWith(".xlsx")
                || fileName.endsWith(".ppt")
                || fileName.endsWith(".pptx")
                || fileName.endsWith(".zip")
                || fileName.endsWith(".png")
                || fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".gif")
                || fileName.endsWith(".bmp")
                || fileName.endsWith(".webp");
    }

    private String validatePythonScriptWrite(String logicalPath, String content) {
        if (!StringUtils.hasText(logicalPath)) {
            return "";
        }
        return PythonScriptWritePolicy.validateWorkspacePythonScript(logicalPath, content);
    }

    private String stringValue(Map<String, Object> payload, String key) {
        if (payload == null || key == null || key.isBlank()) {
            return "";
        }
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String escapeJson(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
