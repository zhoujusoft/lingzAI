package lingzhou.agent.backend.capability.agentruntime;

import java.util.Objects;

public record AgentRuntimeProfileResolution(AgentRuntimeProfile profile, AgentRuntimePipeline pipeline) {

    public AgentRuntimeProfileResolution {
        profile = Objects.requireNonNull(profile, "profile");
        pipeline = Objects.requireNonNull(pipeline, "pipeline");
    }

    public boolean usesToolAwarePipeline() {
        return pipeline == AgentRuntimePipeline.TOOL_AWARE;
    }
}
