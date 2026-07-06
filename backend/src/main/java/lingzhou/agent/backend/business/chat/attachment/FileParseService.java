package lingzhou.agent.backend.business.chat.attachment;

import com.alibaba.fastjson.JSON;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeWorkspace;
import lingzhou.agent.backend.business.chat.execution.nativefs.LogicalPathResolver;
import lingzhou.agent.backend.business.chat.execution.nativefs.PathJail;
import lingzhou.agent.backend.business.chat.execution.nativefs.SandboxViolationException;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeToolContext;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeWorkspaceResolver;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FileParseService {

    private final ChatFileService chatFileService;
    private final List<FileParseProvider> providers;
    private final RuntimeWorkspaceResolver runtimeWorkspaceResolver;
    private final LogicalPathResolver logicalPathResolver;

    public FileParseService(
            ChatFileService chatFileService,
            List<FileParseProvider> providers,
            RuntimeWorkspaceResolver runtimeWorkspaceResolver,
            LogicalPathResolver logicalPathResolver) {
        this.chatFileService = chatFileService;
        this.providers = providers == null
                ? List.of()
                : providers.stream()
                        .sorted(Comparator.comparingInt(FileParseProvider::order))
                        .toList();
        this.runtimeWorkspaceResolver = runtimeWorkspaceResolver;
        this.logicalPathResolver = logicalPathResolver;
    }

    public List<FileParseResult> parseUploads(List<String> fileIds, FileParseMode mode) {
        List<ChatFileService.UploadedFile> files = chatFileService.resolveFiles(fileIds);
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<FileParseResult> results = new ArrayList<>();
        for (ChatFileService.UploadedFile file : files) {
            FileParseResult result = parseUploadedFile(file, mode);
            if (result != null) {
                results.add(result);
            }
        }
        return List.copyOf(results);
    }

    public FileParseResult parseUploadedFile(RuntimeToolContext context, String pathOrFileName, FileParseMode mode) {
        if (context == null) {
            return FileParseResults.unsupported(pathOrFileName, "运行时上下文为空");
        }
        String canonicalPath = chatFileService.canonicalizeLogicalUploadPath(context.fileListJson(), pathOrFileName);
        List<ChatFileService.UploadedFile> files = chatFileService.resolveFilesFromFileListJson(context.fileListJson());
        ChatFileService.UploadedFile matched = matchUploadedFile(files, canonicalPath);
        if (matched != null) {
            return parseUploadedFile(matched, mode);
        }
        ChatFileService.UploadedFile runtimeFile = resolveRuntimeLogicalFile(context, canonicalPath);
        if (runtimeFile != null) {
            return parseUploadedFile(runtimeFile, mode);
        }
        if (files.isEmpty()) {
            return FileParseResults.unsupported(pathOrFileName, "当前会话没有可解析的上传附件或 runtime 文件");
        }
        return FileParseResults.unsupported(pathOrFileName, "未找到匹配的上传附件或 runtime 文件: " + pathOrFileName);
    }

    public String buildPromptContext(List<FileParseResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        String payload = JSON.toJSONString(toSerializablePayload(results));
        return "\n\nSystem parsed attachment context:\n" + payload
                + "\n\nThe system parsed these attachments on demand for the current task. "
                + "Use the parsed attachment context above as the primary file evidence. "
                + "When mode=structured, the context intentionally keeps only compact schema and short previews to preserve prompt budget. "
                + "If the parsed context is partial or insufficient, explicitly explain the limitation instead of fabricating file contents.";
    }

    public List<Map<String, Object>> toSerializablePayload(List<FileParseResult> results) {
        return FileParsePayloadBuilder.buildAll(results);
    }

    private ChatFileService.UploadedFile matchUploadedFile(
            List<ChatFileService.UploadedFile> files, String pathOrFileName) {
        if (files == null || files.isEmpty() || !StringUtils.hasText(pathOrFileName)) {
            return null;
        }
        String normalized = pathOrFileName.trim();
        String normalizedFileName = normalized.startsWith("/uploads/")
                ? normalized.substring("/uploads/".length()).trim()
                : normalized;
        for (ChatFileService.UploadedFile file : files) {
            if (file == null || !StringUtils.hasText(file.name())) {
                continue;
            }
            if (normalized.equals(file.name())
                    || normalizedFileName.equals(file.name())
                    || normalized.equals("/uploads/" + file.name())) {
                return file;
            }
        }
        return null;
    }

    private FileParseResult parseUploadedFile(ChatFileService.UploadedFile file, FileParseMode mode) {
        if (file == null || !StringUtils.hasText(file.name())) {
            return FileParseResults.unsupported("", "上传附件信息为空");
        }
        for (FileParseProvider provider : providers) {
            if (provider == null || !provider.supports(file, mode)) {
                continue;
            }
            FileParseResult result = provider.parse(file, mode);
            if (result != null) {
                return result;
            }
        }
        return FileParseResults.unsupported(file.name(), "当前附件暂无可用解析器: " + file.name());
    }

    private ChatFileService.UploadedFile resolveRuntimeLogicalFile(RuntimeToolContext context, String pathOrFileName) {
        if (context == null || !StringUtils.hasText(pathOrFileName)) {
            return null;
        }
        String normalizedPath = pathOrFileName.trim();
        if (!normalizedPath.startsWith("/")) {
            return null;
        }
        try {
            RuntimeWorkspace workspace = runtimeWorkspaceResolver.resolve(
                    context.userId(),
                    context.sessionId(),
                    context.currentRuntimeSkillName(),
                    context.scopeType(),
                    context.scopeId());
            PathJail jail = new PathJail(workspace.sandboxRoots());
            Path hostPath = jail.assertReadable(logicalPathResolver.resolve(workspace, normalizedPath));
            if (!Files.exists(hostPath) || !Files.isRegularFile(hostPath)) {
                return null;
            }
            return chatFileService.createRuntimeLocalUploadedFile(normalizedPath, hostPath);
        } catch (IllegalArgumentException | SandboxViolationException | java.io.IOException ignored) {
            return null;
        }
    }
}
