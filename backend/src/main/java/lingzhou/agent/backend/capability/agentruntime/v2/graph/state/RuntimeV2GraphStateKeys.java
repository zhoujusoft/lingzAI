package lingzhou.agent.backend.capability.agentruntime.v2.graph.state;

public final class RuntimeV2GraphStateKeys {

    public static final String TRIAGE_NODE = "triageNode";
    public static final String REASONING_NODE = "reasoningNode";
    public static final String ACTION_NODE = "actionNode";
    public static final String OBSERVATION_NODE = "observationNode";
    public static final String CODE_ESCALATION_NODE = "codeEscalationNode";
    public static final String FINAL_ANSWER_NODE = "finalAnswerNode";
    public static final String LIMIT_EXCEEDED_NODE = "limitExceededNode";

    public static final String ROUTE = "route";
    public static final String SESSION_ID = "sessionId";
    public static final String USER_ID = "userId";
    public static final String USER_REQUEST = "userRequest";
    public static final String PARAMS_JSON = "paramsJson";
    public static final String FILE_LIST_JSON = "fileListJson";
    public static final String EXECUTION_MODE_HINT = "executionModeHint";
    public static final String MODE = "mode";
    public static final String MESSAGES = "messages";
    public static final String PHASE = "phase";
    public static final String ITERATION_COUNT = "iterationCount";
    public static final String MAX_ITERATIONS = "maxIterations";
    public static final String LLM_CALL_COUNT = "llmCallCount";
    public static final String TOOL_CALL_COUNT = "toolCallCount";
    public static final String FINISH_REASON = "finishReason";
    public static final String FINAL_ANSWER = "finalAnswer";
    public static final String FINAL_ANSWER_DRAFT = "finalAnswerDraft";
    public static final String TERMINAL_ANSWER_STREAMED = "terminalAnswerStreamed";
    public static final String CONTINUE_REASONING = "continueReasoning";
    public static final String NEEDS_TOOL_CALL = "needsToolCall";
    public static final String NEEDS_CODE_ESCALATION = "needsCodeEscalation";
    public static final String AVAILABLE_TOOL_NAMES = "availableToolNames";
    public static final String RUNTIME_CONTEXT_KEY = "runtimeContextKey";
    public static final String LAST_DECISION = "lastDecision";
    public static final String LAST_TOOL_CALL_ID = "lastToolCallId";
    public static final String LAST_TOOL_NAME = "lastToolName";
    public static final String LAST_TOOL_ARGUMENTS = "lastToolArguments";
    public static final String LAST_TOOL_RESULT = "lastToolResult";
    public static final String LAST_OBSERVATION = "lastObservation";
    public static final String OBSERVATION_TRACE = "observationTrace";
    public static final String OBSERVATION_STATE = "observationState";
    public static final String TOOL_STATE = "toolState";
    public static final String WORKING_MEMORY = "workingMemory";
    public static final String DOCUMENT_STATE = "documentState";
    public static final String CODE_STATE = "codeState";
    public static final String COMPLETION_STATE = "completionState";
    public static final String GRAPH_RUNTIME_STATUS = "graphRuntimeStatus";
    public static final String GRAPH_READY = "graphReady";

    private RuntimeV2GraphStateKeys() {}
}
