package lingzhou.agent.backend.capability.agentruntime.v2.graph.state;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Mode;
import org.springframework.ai.chat.messages.Message;

public record RuntimeV2GraphSeed(
        String sessionId,
        Long userId,
        String userRequest,
        String paramsJson,
        String fileListJson,
        String executionModeHint,
        RuntimeV2Mode mode,
        int maxIterations,
        List<Message> messages,
        List<String> availableToolNames,
        String runtimeContextKey) {

    public Map<String, Object> toStateMap() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(RuntimeV2GraphStateKeys.SESSION_ID, sessionId == null ? "" : sessionId.trim());
        state.put(RuntimeV2GraphStateKeys.USER_ID, userId == null ? 0L : userId);
        state.put(RuntimeV2GraphStateKeys.USER_REQUEST, userRequest == null ? "" : userRequest.trim());
        state.put(RuntimeV2GraphStateKeys.PARAMS_JSON, paramsJson == null ? "" : paramsJson.trim());
        state.put(RuntimeV2GraphStateKeys.FILE_LIST_JSON, fileListJson == null ? "" : fileListJson.trim());
        state.put(
                RuntimeV2GraphStateKeys.EXECUTION_MODE_HINT, executionModeHint == null ? "" : executionModeHint.trim());
        state.put(RuntimeV2GraphStateKeys.MODE, mode == null ? RuntimeV2Mode.DIRECT.name() : mode.name());
        state.put(RuntimeV2GraphStateKeys.MESSAGES, messages == null ? List.of() : List.copyOf(messages));
        state.put(RuntimeV2GraphStateKeys.MAX_ITERATIONS, Math.max(0, maxIterations));
        state.put(RuntimeV2GraphStateKeys.ITERATION_COUNT, 0);
        state.put(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, 0);
        state.put(RuntimeV2GraphStateKeys.TOOL_CALL_COUNT, 0);
        state.put(RuntimeV2GraphStateKeys.ROUTE, "");
        state.put(RuntimeV2GraphStateKeys.PHASE, "");
        state.put(RuntimeV2GraphStateKeys.FINISH_REASON, "");
        state.put(RuntimeV2GraphStateKeys.FINAL_ANSWER, "");
        state.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "");
        state.put(RuntimeV2GraphStateKeys.TERMINAL_ANSWER_STREAMED, Boolean.FALSE);
        state.put(RuntimeV2GraphStateKeys.CONTINUE_REASONING, Boolean.FALSE);
        state.put(RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL, Boolean.FALSE);
        state.put(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, Boolean.FALSE);
        state.put(RuntimeV2GraphStateKeys.LAST_DECISION, Map.of());
        state.put(RuntimeV2GraphStateKeys.LAST_TOOL_CALL_ID, "");
        state.put(RuntimeV2GraphStateKeys.LAST_TOOL_NAME, "");
        state.put(RuntimeV2GraphStateKeys.LAST_TOOL_ARGUMENTS, Map.of());
        state.put(RuntimeV2GraphStateKeys.LAST_TOOL_RESULT, Map.of());
        state.put(RuntimeV2GraphStateKeys.LAST_OBSERVATION, "");
        state.put(RuntimeV2GraphStateKeys.OBSERVATION_TRACE, List.of());
        state.put(RuntimeV2GraphStateKeys.OBSERVATION_STATE, Map.of());
        state.put(RuntimeV2GraphStateKeys.TOOL_STATE, Map.of());
        state.put(RuntimeV2GraphStateKeys.WORKING_MEMORY, Map.of());
        state.put(RuntimeV2GraphStateKeys.DOCUMENT_STATE, Map.of());
        state.put(RuntimeV2GraphStateKeys.CODE_STATE, Map.of());
        state.put(RuntimeV2GraphStateKeys.COMPLETION_STATE, Map.of());
        state.put(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS, "");
        state.put(
                RuntimeV2GraphStateKeys.AVAILABLE_TOOL_NAMES,
                availableToolNames == null ? List.of() : List.copyOf(availableToolNames));
        state.put(
                RuntimeV2GraphStateKeys.RUNTIME_CONTEXT_KEY, runtimeContextKey == null ? "" : runtimeContextKey.trim());
        state.put(RuntimeV2GraphStateKeys.GRAPH_READY, Boolean.TRUE);
        return state;
    }
}
