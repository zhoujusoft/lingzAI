package lingzhou.agent.backend.skillstudio.validate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lingzhou.agent.backend.skillstudio.SkillStudioWorkspacePaths;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioChangeProposal;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioFileChange;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioFileType;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationIssue;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultSkillStudioValidationService implements SkillStudioValidationService {

    private static final Pattern REFERENCE_LINK_PATTERN = Pattern.compile("references/([A-Za-z0-9._\\-/]+)");
    private static final Pattern SCRIPT_LINK_PATTERN = Pattern.compile("/skill/scripts/([A-Za-z0-9._\\-/]+\\.py)");
    private static final Pattern LEGACY_RUNTIME_TOOL_PATTERN = Pattern.compile("runtime_tool\\s*\\(");

    @Override
    public SkillStudioValidationResult validateProposal(SkillStudioChangeProposal proposal) {
        if (proposal == null) {
            return new SkillStudioValidationResult(
                    false, List.of(issue("", "EMPTY_PROPOSAL", "proposal 不能为空")), List.of());
        }
        List<SkillStudioValidationIssue> errors = new ArrayList<>();
        List<SkillStudioValidationIssue> warnings = new ArrayList<>();
        String skillMdContent = null;
        List<String> referencePaths = new ArrayList<>();
        List<String> scriptPaths = new ArrayList<>();
        List<SkillStudioFileChange> scriptChanges = new ArrayList<>();
        boolean hasScriptChange = false;
        boolean hasRequirementsChange = false;
        if (proposal.changes() == null || proposal.changes().isEmpty()) {
            errors.add(issue("", "EMPTY_CHANGES", "changes 不能为空"));
            return new SkillStudioValidationResult(false, List.copyOf(errors), List.copyOf(warnings));
        }
        for (SkillStudioFileChange change : proposal.changes()) {
            if (change == null) {
                errors.add(issue("", "EMPTY_CHANGE", "change 不能为空"));
                continue;
            }
            if (change.fileType() == SkillStudioFileType.SKILL) {
                skillMdContent = change.content();
            }
            if (change.fileType() == SkillStudioFileType.REFERENCE) {
                referencePaths.add(normalizeReferencePath(change.path()));
            }
            if (change.fileType() == SkillStudioFileType.SCRIPT) {
                hasScriptChange = true;
                scriptPaths.add(normalizeScriptPath(change.path()));
                scriptChanges.add(change);
            }
            if (change.fileType() == SkillStudioFileType.REQUIREMENTS) {
                hasRequirementsChange = true;
            }
            if (isOutOfScope(change, proposal.skillName())) {
                errors.add(issue(change.path(), "OUT_OF_SKILL_SCOPE", "不允许写入当前 skill draft 目录外文件"));
            }
        }
        if (hasScriptChange && !hasRequirementsChange) {
            errors.add(issue(
                    SkillStudioWorkspacePaths.DRAFT_ROOT + "/" + proposal.skillName() + "/requirements.txt",
                    "MISSING_REQUIREMENTS",
                    "生成或修改 Python 脚本时必须同步生成 skill 根目录 requirements.txt"));
        }
        errors.addAll(validateSkillContentInternal(proposal.skillName(), skillMdContent, referencePaths, scriptPaths));
        errors.addAll(validateScriptChanges(skillMdContent, scriptChanges));
        return new SkillStudioValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }

    @Override
    public SkillStudioValidationResult validateSkillContent(String skillName, String skillMdContent) {
        List<SkillStudioValidationIssue> errors =
                validateSkillContentInternal(skillName, skillMdContent, List.of(), List.of());
        return new SkillStudioValidationResult(errors.isEmpty(), List.copyOf(errors), List.of());
    }

    private List<SkillStudioValidationIssue> validateSkillContentInternal(
            String skillName, String skillMdContent, List<String> referencePaths, List<String> scriptPaths) {
        List<SkillStudioValidationIssue> errors = new ArrayList<>();
        if (!StringUtils.hasText(skillMdContent)) {
            errors.add(issue("SKILL.md", "MISSING_SKILL_CONTENT", "SKILL.md 内容不能为空"));
            return errors;
        }
        if (!skillMdContent.stripLeading().startsWith("---")) {
            errors.add(issue("SKILL.md", "MISSING_FRONTMATTER", "frontmatter 缺失"));
        }
        if (!skillMdContent.contains("\nname:")) {
            errors.add(issue("SKILL.md", "MISSING_NAME", "frontmatter 缺少 name"));
        } else if (!skillMdContent.contains("name: " + skillName)) {
            errors.add(issue("SKILL.md", "NAME_MISMATCH", "frontmatter name 与 skillName 不一致"));
        }
        if (!skillMdContent.contains("\ndescription:")) {
            errors.add(issue("SKILL.md", "MISSING_DESCRIPTION", "frontmatter 缺少 description"));
        }
        Matcher matcher = REFERENCE_LINK_PATTERN.matcher(skillMdContent);
        while (matcher.find()) {
            String path = matcher.group(1);
            if (!referencePaths.contains(path)) {
                errors.add(issue("SKILL.md", "REFERENCE_NOT_FOUND", "引用的 reference 不存在: " + path));
            }
        }
        Matcher scriptMatcher = SCRIPT_LINK_PATTERN.matcher(skillMdContent);
        while (scriptMatcher.find()) {
            String path = scriptMatcher.group(1);
            if (!scriptPaths.isEmpty() && !scriptPaths.contains(path)) {
                errors.add(issue("SKILL.md", "SCRIPT_NOT_FOUND", "引用的 Python 脚本不存在: scripts/" + path));
            }
        }
        errors.addAll(validateRuntimeToolActions(skillMdContent));
        return errors;
    }

    private List<SkillStudioValidationIssue> validateScriptChanges(
            String skillMdContent, List<SkillStudioFileChange> scriptChanges) {
        List<SkillStudioValidationIssue> errors = new ArrayList<>();
        if (scriptChanges == null || scriptChanges.isEmpty()) {
            return errors;
        }
        if (!StringUtils.hasText(skillMdContent)) {
            return errors;
        }
        if (!containsDirectRunPythonInvocation(skillMdContent)) {
            errors.add(issue(
                    "SKILL.md",
                    "MISSING_RUN_PYTHON_INVOCATION",
                    "包含 Python 脚本时，SKILL.md 必须写明独立工具 run_python(...) 调用方式"));
        }
        if (containsWriteArtifactInvocation(skillMdContent)
                && mentionsBinaryArtifact(skillMdContent)
                && !containsKey(skillMdContent, "sourcePath")) {
            errors.add(issue(
                    "SKILL.md",
                    "MISSING_WRITE_ARTIFACT_SOURCE_PATH",
                    "二进制下载产物必须通过 write_artifact 的 sourcePath 指向脚本已生成文件，不能通过 content 重写"));
        }
        for (SkillStudioFileChange change : scriptChanges) {
            String scriptPath = normalizeScriptPath(change.path());
            if (StringUtils.hasText(scriptPath) && !skillMdContent.contains("/skill/scripts/" + scriptPath)) {
                errors.add(issue(
                        "SKILL.md",
                        "MISSING_SCRIPT_RUNTIME_PATH",
                        "SKILL.md 必须使用 /skill/scripts/" + scriptPath + " 描述脚本调用路径"));
            }
            String content = change.content() == null ? "" : change.content();
            if (containsPlaceholderImplementation(content)) {
                errors.add(issue(
                        change.path(),
                        "SCRIPT_PLACEHOLDER_IMPLEMENTATION",
                        "Python 脚本不能包含模拟、TODO、placeholder 或“实际应替换”等核心占位实现"));
            }
            if (mentionsDocx(content) && !usesDocxLibrary(content)) {
                errors.add(issue(
                        change.path(), "DOCX_LIBRARY_MISSING", "处理 .docx 的 Python 脚本必须使用真实 DOCX 解析库，例如 python-docx"));
            }
        }
        return errors;
    }

    private boolean containsDirectRunPythonInvocation(String content) {
        return content != null && content.contains("run_python(");
    }

    private boolean containsWriteArtifactInvocation(String content) {
        return content != null && content.contains("write_artifact(");
    }

    private boolean containsKey(String content, String key) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(key)) {
            return false;
        }
        return content.contains("\"" + key + "\"")
                || content.contains("'" + key + "'")
                || Pattern.compile("(^|[^A-Za-z0-9_])" + Pattern.quote(key) + "\\s*=", Pattern.MULTILINE)
                        .matcher(content)
                        .find();
    }

    private boolean containsPlaceholderImplementation(String content) {
        String normalized = content == null ? "" : content.toLowerCase();
        return content != null
                && (content.contains("模拟")
                        || content.contains("实际应")
                        || content.contains("仅作示例")
                        || content.contains("简化处理")
                        || content.contains("假设输入")
                        || normalized.contains("todo")
                        || normalized.contains("placeholder")
                        || normalized.contains("notimplementederror"));
    }

    private boolean mentionsDocx(String content) {
        return content != null && content.toLowerCase().contains(".docx");
    }

    private boolean mentionsBinaryArtifact(String content) {
        if (content == null) {
            return false;
        }
        String normalized = content.toLowerCase();
        return normalized.contains(".docx")
                || normalized.contains(".xlsx")
                || normalized.contains(".pptx")
                || normalized.contains(".pdf")
                || normalized.contains(".zip")
                || normalized.contains(".png")
                || normalized.contains(".jpg")
                || normalized.contains(".jpeg");
    }

    private boolean usesDocxLibrary(String content) {
        return content != null && (content.contains("from docx import Document") || content.contains("docx.Document"));
    }

    private List<SkillStudioValidationIssue> validateRuntimeToolActions(String skillMdContent) {
        List<SkillStudioValidationIssue> errors = new ArrayList<>();
        if (!StringUtils.hasText(skillMdContent)) {
            return errors;
        }
        Matcher matcher = LEGACY_RUNTIME_TOOL_PATTERN.matcher(skillMdContent);
        while (matcher.find()) {
            errors.add(
                    issue(
                            "SKILL.md",
                            "LEGACY_RUNTIME_TOOL_NOT_ALLOWED",
                            "新 skill 不允许继续使用 runtime_tool(...)；请改用独立工具 file_read/file_write/run_python/write_artifact/list_dir/stat"));
        }
        return errors;
    }

    private boolean isOutOfScope(SkillStudioFileChange change, String skillName) {
        if (change == null || !StringUtils.hasText(change.path()) || !StringUtils.hasText(skillName)) {
            return true;
        }
        String normalized = change.path().trim().replace("\\", "/");
        String normalizedSkillName = skillName.trim();
        if (!normalized.startsWith(SkillStudioWorkspacePaths.DRAFT_ROOT + "/" + normalizedSkillName + "/")
                || normalized.contains("..")) {
            return true;
        }
        return switch (change.fileType()) {
            case SKILL -> !normalized.equals(
                    SkillStudioWorkspacePaths.DRAFT_ROOT + "/" + normalizedSkillName + "/SKILL.md");
            case REFERENCE -> !normalized.startsWith(
                    SkillStudioWorkspacePaths.DRAFT_ROOT + "/" + normalizedSkillName + "/references/");
            case SCRIPT -> !normalized.startsWith(
                            SkillStudioWorkspacePaths.DRAFT_ROOT + "/" + normalizedSkillName + "/scripts/")
                    || !normalized.endsWith(".py");
            case REQUIREMENTS -> !normalized.equals(
                    SkillStudioWorkspacePaths.DRAFT_ROOT + "/" + normalizedSkillName + "/requirements.txt");
        };
    }

    private String normalizeReferencePath(String path) {
        String normalized = path == null ? "" : path.trim().replace("\\", "/");
        int index = normalized.indexOf("/references/");
        if (index < 0) {
            return normalized.replaceFirst("^references/", "");
        }
        return normalized.substring(index + "/references/".length());
    }

    private String normalizeScriptPath(String path) {
        String normalized = path == null ? "" : path.trim().replace("\\", "/");
        int index = normalized.indexOf("/scripts/");
        if (index < 0) {
            return normalized.replaceFirst("^scripts/", "");
        }
        return normalized.substring(index + "/scripts/".length());
    }

    private SkillStudioValidationIssue issue(String path, String code, String message) {
        return new SkillStudioValidationIssue(path, code, message);
    }
}
