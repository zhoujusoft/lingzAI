package lingzhou.agent.backend.capability.agentruntime.personal;

public record PersonalAgentExecutionStep(
        String stepId, String name, String type, String executor, String target, String status) {}
