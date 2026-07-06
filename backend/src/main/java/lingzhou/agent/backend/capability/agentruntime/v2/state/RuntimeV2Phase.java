package lingzhou.agent.backend.capability.agentruntime.v2.state;

public enum RuntimeV2Phase {
    TRIAGE,
    REASONING,
    ACTION,
    OBSERVATION,
    FINAL_CANDIDATE,
    COMPLETION_CHECK,
    FINAL_STREAMING,
    FINALIZING,
    CANCELLED,
    COMPLETED,
    FAILED
}
