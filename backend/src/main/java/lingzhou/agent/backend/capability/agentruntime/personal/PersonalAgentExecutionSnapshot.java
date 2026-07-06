package lingzhou.agent.backend.capability.agentruntime.personal;

public record PersonalAgentExecutionSnapshot(
        PersonalAgentExecutionMeta personalAgent,
        PersonalAgentExecutionPlan executionPlan,
        PersonalAgentExecutionState executionState,
        PersonalAgentExecutionPrecheck executionPrecheck) {}
