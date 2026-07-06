package lingzhou.agent.backend.capability.agentruntime.v2.state;

public enum RuntimeV2FinishReason {
    DIRECT_ANSWER,
    COMPLETED,
    LIMIT_EXCEEDED,
    CANCELLED,
    WAITING_APPROVAL,
    MODEL_ERROR,
    TOOL_ERROR,
    GRAPH_RUNTIME_UNAVAILABLE
}
