package lingzhou.agent.backend.skillstudio.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationIssue;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

final class SkillStudioCreatorToolProvider {

    private static final Pattern LEGACY_RUNTIME_TOOL_PATTERN = Pattern.compile("runtime_tool\\s*\\(");
    private final Path workspaceRoot;
    private final Path draftRoot;
    private final Path templatesRoot;
    private final Path creatorSkillRoot;
    private final String skillName;
    private final Path skillDraftRoot;
    private final List<String> logs = new ArrayList<>();
    private final List<String> writtenScriptPaths = new ArrayList<>();
    private String latestSkillMarkdown;
    private boolean requirementsWritten;

    SkillStudioCreatorToolProvider(Path workspaceRoot, String skillName) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.draftRoot =
                this.workspaceRoot.resolve(SkillStudioWorkspacePaths.DRAFT_ROOT).normalize();
        this.templatesRoot = this.workspaceRoot
                .resolve(SkillStudioWorkspacePaths.TEMPLATES_ROOT)
                .normalize();
        this.skillName = skillName == null ? "" : skillName.trim();
        this.skillDraftRoot = this.draftRoot.resolve(this.skillName).normalize();
        this.creatorSkillRoot = this.workspaceRoot
                .resolve(SkillStudioWorkspacePaths.SKILLS_ROOT)
                .resolve("zhuoju-skill-creator")
                .normalize();
    }

    List<ToolCallback> toolCallbacks() {
        return List.of(ToolCallbacks.from(this));
    }

    List<String> logs() {
        return List.copyOf(logs);
    }

    List<String> toolNames() {
        return toolCallbacks().stream()
                .map(callback -> callback.getToolDefinition().name())
                .toList();
    }

    @Tool(
            description =
                    "读取本地 UTF-8 文本文件内容。只允许读取当前 skill draft 目录，以及技能工坊内置模板和 zhuoju-skill-creator 自带模板；禁止读取其他 skill 或其他 draft，避免直接复制现有技能。")
    public String readFile(@ToolParam(description = "相对工作区或绝对路径") String path) {
        Path resolved = resolveReadablePath(path);
        if (resolved == null) {
            logs.add("readFile rejected: " + path);
            return errorJson("仅允许读取当前 skill draft 与技能工坊内置模板，禁止读取其他 skill 或其他 draft");
        }
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            logs.add("readFile missing: " + resolved);
            return errorJson("文件不存在: " + resolved);
        }
        try {
            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            logs.add("readFile ok: " + workspaceRoot.relativize(resolved) + ", chars=" + content.length());
            return content;
        } catch (IOException ex) {
            logs.add("readFile failed: " + resolved + ", error=" + ex.getMessage());
            return errorJson("读取失败: " + ex.getMessage());
        }
    }

    @Tool(description = "将 UTF-8 文本写入技能工坊 draft 目录，仅用于调试或显式写入场景。")
    public String writeFile(
            @ToolParam(description = "相对工作区或绝对路径") String path, @ToolParam(description = "UTF-8 文本内容") String content) {
        Path resolved = resolveWritableDraftPath(path);
        if (resolved == null || !resolved.startsWith(skillDraftRoot)) {
            logs.add("writeFile rejected: " + path);
            return errorJson("仅允许写入当前 skill 的 draft 目录");
        }
        try {
            Path parent = resolved.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String normalizedContent = normalizeWritableContent(resolved, content);
            String validationError = validateWritableContent(resolved, normalizedContent);
            if (validationError != null) {
                logs.add("writeFile rejected by content validation: " + workspaceRoot.relativize(resolved) + ", error="
                        + validationError);
                return errorJson(validationError);
            }
            Files.writeString(resolved, normalizedContent, StandardCharsets.UTF_8);
            recordWrittenFile(resolved, normalizedContent);
            logs.add("writeFile ok: " + workspaceRoot.relativize(resolved) + ", chars=" + normalizedContent.length());
            return "{\"success\":true,\"path\":\"" + escapeJson(resolved.toString()) + "\"}";
        } catch (IOException ex) {
            logs.add("writeFile failed: " + resolved + ", error=" + ex.getMessage());
            return errorJson("写入失败: " + ex.getMessage());
        }
    }

    SkillStudioValidationResult validateWrittenBundle() {
        List<SkillStudioValidationIssue> errors = new ArrayList<>();
        if (writtenScriptPaths.isEmpty()) {
            return new SkillStudioValidationResult(true, List.of(), List.of());
        }
        String skillMarkdown = latestSkillMarkdown;
        if (skillMarkdown == null) {
            Path skillFile = skillDraftRoot.resolve("SKILL.md").normalize();
            if (Files.isRegularFile(skillFile)) {
                try {
                    skillMarkdown = Files.readString(skillFile, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    errors.add(issue("SKILL.md", "SKILL_READ_FAILED", "读取 SKILL.md 失败: " + ex.getMessage()));
                }
            }
        }
        if (!StringUtils.hasText(skillMarkdown)) {
            errors.add(issue("SKILL.md", "MISSING_SKILL_CONTENT", "写入 Python 脚本时必须同时维护 SKILL.md"));
        } else {
            errors.addAll(validateRuntimeToolActions(skillMarkdown));
            if (!skillMarkdown.contains("run_python(")) {
                errors.add(issue(
                        "SKILL.md",
                        "MISSING_RUN_PYTHON_INVOCATION",
                        "写入 Python 脚本时，SKILL.md 必须包含独立工具 run_python(...) 调用方式"));
            }
            for (String scriptPath : writtenScriptPaths) {
                if (!skillMarkdown.contains("/skill/scripts/" + scriptPath)) {
                    errors.add(issue(
                            "SKILL.md",
                            "MISSING_SCRIPT_RUNTIME_PATH",
                            "SKILL.md 必须描述 /skill/scripts/" + scriptPath + " 的运行时调用"));
                }
            }
        }
        if (!requirementsWritten && !Files.isRegularFile(skillDraftRoot.resolve("requirements.txt"))) {
            errors.add(issue("requirements.txt", "MISSING_REQUIREMENTS", "写入 Python 脚本时必须维护 requirements.txt"));
        }
        return new SkillStudioValidationResult(errors.isEmpty(), List.copyOf(errors), List.of());
    }

    @Tool(description = "生成最终交付产物。当前调试链路仅返回占位结果，不实际上传对象存储。")
    public String writeArtifact(
            @ToolParam(description = "对象存储目录前缀") String folder,
            @ToolParam(description = "输出文件名") String fileName,
            @ToolParam(description = "UTF-8 文本内容，可选") String content,
            @ToolParam(description = "本地源文件路径，可选") String sourcePath,
            @ToolParam(description = "MIME 类型，可选") String contentType) {
        logs.add("writeArtifact simulated: folder=" + folder + ", fileName=" + fileName + ", sourcePath=" + sourcePath);
        String resolvedFolder = folder == null || folder.isBlank() ? "skillstudio/artifacts" : folder.trim();
        String resolvedFileName = fileName == null || fileName.isBlank() ? "artifact" : fileName.trim();
        String extension = "";
        int dot = resolvedFileName.lastIndexOf('.');
        if (dot >= 0 && dot < resolvedFileName.length() - 1) {
            extension = resolvedFileName.substring(dot);
        }
        String objectName = resolvedFolder + "/" + UUID.randomUUID() + extension;
        String artifactId = MinioService.toArtifactId(objectName);
        String artifactShortId = MinioService.toArtifactShortId(objectName);
        String path = "artifact://documents/" + objectName;
        String downloadUrl = "/api/files/artifacts/"
                + artifactId
                + "/download?fileName="
                + java.net.URLEncoder.encode(resolvedFileName, java.nio.charset.StandardCharsets.UTF_8);
        return """
                {
                  "success": true,
                  "file": {
                    "id": "%s",
                    "fileName": "%s",
                    "size": null,
                    "bucket": "documents",
                    "objectName": "%s",
                    "path": "%s",
                    "downloadUrl": "%s",
                    "contentType": "%s"
                  },
                  "bucket": "documents",
                  "objectName": "%s",
                  "fileName": "%s",
                  "path": "%s",
                  "mode": "simulated"
                }
                """
                .formatted(
                        escapeJson(artifactShortId),
                        escapeJson(resolvedFileName),
                        escapeJson(objectName),
                        escapeJson(path),
                        escapeJson(downloadUrl),
                        escapeJson(contentType == null ? "" : contentType),
                        escapeJson(objectName),
                        escapeJson(resolvedFileName),
                        escapeJson(path));
    }

    private Path resolveReadablePath(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return null;
        }
        Path raw = Path.of(pathValue);
        Path resolved =
                raw.isAbsolute() ? raw.normalize() : workspaceRoot.resolve(raw).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            return null;
        }
        if (resolved.startsWith(skillDraftRoot)
                || resolved.startsWith(creatorSkillRoot)
                || resolved.startsWith(templatesRoot)) {
            return resolved;
        }
        return null;
    }

    private Path resolveWritableDraftPath(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return null;
        }
        String normalized = pathValue.trim().replace("\\", "/");
        Path resolved;
        if (normalized.startsWith(SkillStudioWorkspacePaths.DRAFT_ROOT + "/")) {
            resolved = workspaceRoot.resolve(normalized).normalize();
        } else if (Path.of(pathValue).isAbsolute()) {
            resolved = Path.of(pathValue).normalize();
        } else {
            resolved = skillDraftRoot.resolve(normalized).normalize();
        }
        return resolved.startsWith(workspaceRoot) ? resolved : null;
    }

    private String normalizeWritableContent(Path resolved, String content) {
        String normalized = normalizeLineEnding(content == null ? "" : content);
        if (isSkillMarkdown(resolved)) {
            return normalizeSkillMarkdown(normalized);
        }
        if (isMarkdownFile(resolved)) {
            return ensureTrailingNewline(normalized);
        }
        return normalized;
    }

    private String validateWritableContent(Path resolved, String content) {
        if (isSkillMarkdown(resolved)) {
            List<SkillStudioValidationIssue> invalidRuntimeIssues = validateRuntimeToolActions(content);
            if (!invalidRuntimeIssues.isEmpty()) {
                return invalidRuntimeIssues.get(0).message();
            }
        }
        if (isSkillMarkdown(resolved) && content != null && content.contains("/skill/scripts/")) {
            if (!content.contains("run_python(")) {
                return "SKILL.md 引用 Python 脚本时，必须写出独立工具 run_python(...) 调用方式";
            }
        }
        if (isPythonScript(resolved)) {
            String lower = content == null ? "" : content.toLowerCase();
            if (content != null
                    && (content.contains("模拟")
                            || content.contains("实际应")
                            || content.contains("仅作示例")
                            || content.contains("简化处理")
                            || content.contains("假设输入")
                            || lower.contains("todo")
                            || lower.contains("placeholder")
                            || lower.contains("notimplementederror"))) {
                return "Python 脚本不能包含模拟、TODO、placeholder 或“实际应替换”等核心占位实现";
            }
            if (lower.contains(".docx") && !usesDocxLibrary(content)) {
                return "处理 .docx 的 Python 脚本必须使用真实 DOCX 解析库，例如 python-docx";
            }
        }
        return null;
    }

    private void recordWrittenFile(Path resolved, String content) {
        if (isSkillMarkdown(resolved)) {
            latestSkillMarkdown = content;
            return;
        }
        if (isPythonScript(resolved)) {
            String relative = skillDraftRoot
                    .resolve("scripts")
                    .relativize(resolved)
                    .toString()
                    .replace("\\", "/");
            if (!writtenScriptPaths.contains(relative)) {
                writtenScriptPaths.add(relative);
            }
            return;
        }
        if (resolved.normalize()
                .equals(skillDraftRoot.resolve("requirements.txt").normalize())) {
            requirementsWritten = true;
        }
    }

    private boolean isSkillMarkdown(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        return "SKILL.md".equalsIgnoreCase(path.getFileName().toString())
                && path.normalize().startsWith(skillDraftRoot);
    }

    private boolean isMarkdownFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        return path.getFileName().toString().toLowerCase().endsWith(".md");
    }

    private boolean isPythonScript(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        return path.normalize().startsWith(skillDraftRoot.resolve("scripts"))
                && path.getFileName().toString().toLowerCase().endsWith(".py");
    }

    private boolean usesDocxLibrary(String content) {
        return content != null && (content.contains("from docx import Document") || content.contains("docx.Document"));
    }

    private List<SkillStudioValidationIssue> validateRuntimeToolActions(String skillMarkdown) {
        List<SkillStudioValidationIssue> errors = new ArrayList<>();
        if (!StringUtils.hasText(skillMarkdown)) {
            return errors;
        }
        Matcher matcher = LEGACY_RUNTIME_TOOL_PATTERN.matcher(skillMarkdown);
        while (matcher.find()) {
            errors.add(
                    issue(
                            "SKILL.md",
                            "LEGACY_RUNTIME_TOOL_NOT_ALLOWED",
                            "新 skill 不允许再使用 runtime_tool(...)；请改用独立工具 file_read/file_write/run_python/write_artifact/list_dir/stat"));
        }
        return errors;
    }

    private SkillStudioValidationIssue issue(String path, String code, String message) {
        return new SkillStudioValidationIssue(path, code, message);
    }

    private String normalizeSkillMarkdown(String markdown) {
        String stripped = stripOuterMarkdownFence(markdown);
        ParsedSkillMarkdown parsed = parseSkillMarkdown(stripped);
        String body = stripOuterMarkdownFence(parsed.body()).trim();
        if (body.isBlank()) {
            body = defaultSkillBody();
        } else if (!body.startsWith("#")) {
            body = "# " + skillName + "\n\n" + body;
        }
        String description = parsed.description();
        if (description == null || description.isBlank()) {
            description = inferDescription(body);
        }
        String frontmatter =
                """
                ---
                name: %s
                description: "%s"
                ---

                """
                        .formatted(skillName, escapeYamlDoubleQuoted(description));
        return ensureTrailingNewline(frontmatter + body.trim());
    }

    private ParsedSkillMarkdown parseSkillMarkdown(String markdown) {
        String normalized = normalizeLineEnding(markdown);
        if (!normalized.startsWith("---")) {
            return new ParsedSkillMarkdown("", normalized);
        }
        int firstNewline = normalized.indexOf('\n');
        if (firstNewline < 0) {
            return new ParsedSkillMarkdown("", normalized);
        }
        int secondDelimiter = normalized.indexOf("\n---", firstNewline + 1);
        if (secondDelimiter < 0) {
            return new ParsedSkillMarkdown("", normalized);
        }
        String frontmatter = normalized.substring(firstNewline + 1, secondDelimiter);
        int bodyStart = secondDelimiter + 4;
        if (bodyStart < normalized.length() && normalized.charAt(bodyStart) == '\n') {
            bodyStart++;
        }
        String body = normalized.substring(bodyStart);
        String description = extractFrontmatterValue(frontmatter, "description");
        return new ParsedSkillMarkdown(description, body);
    }

    private String extractFrontmatterValue(String frontmatter, String key) {
        String prefix = key + ":";
        for (String line : frontmatter.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(prefix)) {
                continue;
            }
            String value = trimmed.substring(prefix.length()).trim();
            return stripYamlQuote(value);
        }
        return "";
    }

    private String stripYamlQuote(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String inferDescription(String body) {
        for (String line : normalizeLineEnding(body).split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()
                    || trimmed.startsWith("#")
                    || trimmed.startsWith("-")
                    || trimmed.startsWith("*")
                    || trimmed.startsWith("```")) {
                continue;
            }
            return shrink(trimmed, 72);
        }
        return "用于处理业务需求";
    }

    private String shrink(String value, int limit) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit);
    }

    private String defaultSkillBody() {
        return """
                # %s

                用于处理以下场景：
                - 待补充
                """
                .formatted(skillName);
    }

    private String stripOuterMarkdownFence(String content) {
        String normalized = normalizeLineEnding(content).trim();
        if (!normalized.startsWith("```") || !normalized.endsWith("```")) {
            return content == null ? "" : normalizeLineEnding(content);
        }
        int firstLineBreak = normalized.indexOf('\n');
        if (firstLineBreak < 0 || firstLineBreak >= normalized.length() - 3) {
            return content == null ? "" : normalizeLineEnding(content);
        }
        String fencedBody = normalized.substring(firstLineBreak + 1, normalized.length() - 3);
        return fencedBody.strip();
    }

    private String normalizeLineEnding(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String ensureTrailingNewline(String content) {
        String normalized = normalizeLineEnding(content);
        if (normalized.endsWith("\n")) {
            return normalized;
        }
        return normalized + "\n";
    }

    private String escapeYamlDoubleQuoted(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String errorJson(String message) {
        return "{\"success\":false,\"error\":\"" + escapeJson(message) + "\"}";
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ParsedSkillMarkdown(String description, String body) {}
}
