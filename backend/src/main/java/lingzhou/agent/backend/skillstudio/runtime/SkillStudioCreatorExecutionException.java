package lingzhou.agent.backend.skillstudio.runtime;

import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;

public class SkillStudioCreatorExecutionException extends RuntimeException {

    private final RuntimeRunUsageSnapshot usageSnapshot;

    public SkillStudioCreatorExecutionException(
            String message, RuntimeRunUsageSnapshot usageSnapshot, Throwable cause) {
        super(message, cause);
        this.usageSnapshot = usageSnapshot;
    }

    public RuntimeRunUsageSnapshot getUsageSnapshot() {
        return usageSnapshot;
    }
}
