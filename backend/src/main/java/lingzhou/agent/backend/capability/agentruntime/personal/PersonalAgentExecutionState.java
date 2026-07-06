package lingzhou.agent.backend.capability.agentruntime.personal;

import java.util.List;

public record PersonalAgentExecutionState(
        String status,
        String currentStepId,
        List<String> completedStepIds,
        String waitingReason,
        String lastError,
        List<PersonalAgentExecutionArtifact> artifacts,
        String updatedAt) {}
