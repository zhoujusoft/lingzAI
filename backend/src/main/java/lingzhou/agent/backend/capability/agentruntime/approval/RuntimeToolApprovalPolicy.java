package lingzhou.agent.backend.capability.agentruntime.approval;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RuntimeToolApprovalPolicy {

    private static final Set<String> APPROVAL_REQUIRED_TOOLS = Set.of();

    public boolean requiresApproval(String toolName) {
        return APPROVAL_REQUIRED_TOOLS.contains(normalizeToolName(toolName));
    }

    public String triggerReason(String toolName) {
        return switch (normalizeToolName(toolName)) {
            case "file_write" -> "文件写入工具会修改工作区内容，需要人工确认目标路径和内容风险。";
            case "run_python" -> "Python 执行工具会运行代码，需要人工确认脚本行为和执行参数。";
            default -> "";
        };
    }

    private String normalizeToolName(String toolName) {
        return toolName == null ? "" : toolName.trim().toLowerCase();
    }
}
