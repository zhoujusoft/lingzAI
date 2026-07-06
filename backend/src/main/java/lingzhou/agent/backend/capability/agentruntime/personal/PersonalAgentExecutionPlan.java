package lingzhou.agent.backend.capability.agentruntime.personal;

import java.util.List;

public record PersonalAgentExecutionPlan(
        String planId, Integer version, String goal, String strategy, List<PersonalAgentExecutionStep> steps) {}
