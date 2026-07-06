package lingzhou.agent.backend.business.chat.execution.model;

public record SandboxRoot(
        String key, String hostPath, String logicalPath, String containerPath, boolean read, boolean write) {}
