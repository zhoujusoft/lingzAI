package lingzhou.agent.backend.business.chat.execution.model;

import java.util.List;

public record RuntimeWorkspace(
        String sessionId,
        Long userId,
        String runtimeSkillName,
        String defaultLogicalWorkDir,
        String workspaceRoot,
        String uploadsRoot,
        String outputsRoot,
        String tempRoot,
        String logsRoot,
        String skillRoot,
        String profileRoot,
        List<SandboxRoot> sandboxRoots) {}
