package lingzhou.agent.backend.business.chat.attachment;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MarkItDownParseProvider implements FileParseProvider {

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("pdf", "pptx", "doc", "docx", "xlsx", "xls", "html", "csv", "json", "xml", "zip", "epub");

    private static final String PYTHON_SCRIPT =
            """
            import json
            import sys
            from markitdown import MarkItDown

            path = sys.argv[1]
            md = MarkItDown(enable_plugins=False)
            result = md.convert(path)
            print(json.dumps({"markdown": result.text_content or ""}, ensure_ascii=False))
            """;

    private final ChatFileService chatFileService;
    private final RuntimeExecutionProperties runtimeExecutionProperties;

    public MarkItDownParseProvider(
            ChatFileService chatFileService, RuntimeExecutionProperties runtimeExecutionProperties) {
        this.chatFileService = chatFileService;
        this.runtimeExecutionProperties = runtimeExecutionProperties;
    }

    @Override
    public String name() {
        return "markitdown";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean supports(ChatFileService.UploadedFile file, FileParseMode mode) {
        String extension = extension(file == null ? null : file.name());
        return StringUtils.hasText(extension) && SUPPORTED_EXTENSIONS.contains(extension);
    }

    @Override
    public FileParseResult parse(ChatFileService.UploadedFile file, FileParseMode mode) {
        if (file == null || !StringUtils.hasText(file.name())) {
            return FileParseResults.unsupported("", "上传附件信息为空");
        }
        Path tempFile = null;
        try {
            tempFile = chatFileService.materializeToLocalPath(file.path());
            ProcessBuilder processBuilder = new ProcessBuilder(
                    runtimeExecutionProperties.getPythonCommand(), "-c", PYTHON_SCRIPT, tempFile.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            boolean finished = process.waitFor(
                    Math.max(30, runtimeExecutionProperties.getCommandTimeoutSeconds()), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new FileParseResult(
                        false,
                        FileParseStatus.FAILED,
                        file.name(),
                        extension(file.name()),
                        name(),
                        mode == null ? FileParseMode.STRUCTURED : mode,
                        "UNKNOWN",
                        AttachmentParseResult.Summary.empty(),
                        FileParseResult.ContentView.empty(),
                        java.util.List.of("MarkItDown 解析超时"),
                        "PARSE_FILE_TIMEOUT");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String error = StringUtils.hasText(output) ? output : "MarkItDown 执行失败";
                if (error.contains("No module named 'markitdown'")) {
                    return FileParseResults.unsupported(file.name(), "服务环境未安装 markitdown，当前无法使用 MarkItDown provider");
                }
                if (isMissingOptionalDependency(error, file.name())) {
                    return FileParseResults.unsupported(
                            file.name(),
                            "当前解析器缺少读取 "
                                    + extension(file.name())
                                    + " 所需依赖，parse_file 无法直接完成该文件解析。请改用 CODE fallback 读取原始文件内容。");
                }
                return new FileParseResult(
                        false,
                        FileParseStatus.FAILED,
                        file.name(),
                        extension(file.name()),
                        name(),
                        mode == null ? FileParseMode.STRUCTURED : mode,
                        "UNKNOWN",
                        AttachmentParseResult.Summary.empty(),
                        FileParseResult.ContentView.empty(),
                        java.util.List.of(error),
                        error);
            }
            var payload = JSON.parseObject(output, new TypeReference<java.util.Map<String, Object>>() {});
            String markdown = payload == null ? "" : String.valueOf(payload.getOrDefault("markdown", ""));
            if (!StringUtils.hasText(markdown)) {
                return new FileParseResult(
                        false,
                        FileParseStatus.FAILED,
                        file.name(),
                        extension(file.name()),
                        name(),
                        mode == null ? FileParseMode.STRUCTURED : mode,
                        "UNKNOWN",
                        AttachmentParseResult.Summary.empty(),
                        FileParseResult.ContentView.empty(),
                        java.util.List.of("MarkItDown 返回空结果"),
                        "PARSE_FILE_OUTPUT_EMPTY");
            }
            return FileParseResults.fromMarkdown(
                    file.name(),
                    extension(file.name()),
                    name(),
                    mode == null ? FileParseMode.STRUCTURED : mode,
                    markdown,
                    List.of());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "MarkItDown provider 执行失败" : ex.getMessage();
            if (message.contains("markitdown")) {
                return FileParseResults.unsupported(file.name(), "MarkItDown provider 当前不可用: " + message);
            }
            return new FileParseResult(
                    false,
                    FileParseStatus.FAILED,
                    file.name(),
                    extension(file.name()),
                    name(),
                    mode == null ? FileParseMode.STRUCTURED : mode,
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

    private boolean isMissingOptionalDependency(String error, String fileName) {
        if (!StringUtils.hasText(error)) {
            return false;
        }
        String normalized = error.toLowerCase();
        String extension = extension(fileName);
        if (normalized.contains("missingdependencyexception")) {
            return true;
        }
        if (normalized.contains("dependencies needed to read")) {
            return true;
        }
        if ("pdf".equals(extension) && normalized.contains("markitdown[pdf]")) {
            return true;
        }
        return false;
    }

    private String extension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).trim().toLowerCase();
    }
}
