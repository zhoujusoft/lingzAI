package lingzhou.agent.backend.business.chat.execution.nativefs;

import java.nio.file.Path;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeWorkspace;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LogicalPathResolver {

    public String normalizeLogicalPath(String logicalPath) {
        String trimmed = StringUtils.hasText(logicalPath) ? logicalPath.trim().replace("\\", "/") : "";
        if (!StringUtils.hasText(trimmed)) {
            return "/workspace";
        }
        if (trimmed.startsWith("/")) {
            return trimmed;
        }
        return "/workspace/" + trimmed;
    }

    public Path resolve(RuntimeWorkspace workspace, String logicalPath) {
        if (workspace == null) {
            throw new IllegalArgumentException("Runtime workspace is required");
        }
        String normalized = normalizeLogicalPath(logicalPath);
        if (normalized.equals("/workspace")) {
            return Path.of(workspace.workspaceRoot());
        }
        if (normalized.startsWith("/workspace/")) {
            return Path.of(workspace.workspaceRoot()).resolve(normalized.substring("/workspace/".length()));
        }
        if (normalized.equals("/uploads")) {
            return Path.of(workspace.uploadsRoot());
        }
        if (normalized.startsWith("/uploads/")) {
            return Path.of(workspace.uploadsRoot()).resolve(normalized.substring("/uploads/".length()));
        }
        if (normalized.equals("/outputs")) {
            return Path.of(workspace.outputsRoot());
        }
        if (normalized.startsWith("/outputs/")) {
            return Path.of(workspace.outputsRoot()).resolve(normalized.substring("/outputs/".length()));
        }
        if (normalized.equals("/temp")) {
            return Path.of(workspace.tempRoot());
        }
        if (normalized.startsWith("/temp/")) {
            return Path.of(workspace.tempRoot()).resolve(normalized.substring("/temp/".length()));
        }
        if (normalized.equals("/logs")) {
            return Path.of(workspace.logsRoot());
        }
        if (normalized.startsWith("/logs/")) {
            return Path.of(workspace.logsRoot()).resolve(normalized.substring("/logs/".length()));
        }
        if (normalized.equals("/skill")) {
            return Path.of(workspace.skillRoot());
        }
        if (normalized.startsWith("/skill/")) {
            return Path.of(workspace.skillRoot()).resolve(normalized.substring("/skill/".length()));
        }
        if (normalized.equals("/profile")) {
            return Path.of(workspace.profileRoot());
        }
        if (normalized.startsWith("/profile/")) {
            return Path.of(workspace.profileRoot()).resolve(normalized.substring("/profile/".length()));
        }
        throw new IllegalArgumentException("不支持的逻辑路径: " + logicalPath);
    }
}
