package lingzhou.agent.backend.capability.agentruntime.v2.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Mode;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Phase;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;

public final class RuntimeV2GraphStateProjector {

    private RuntimeV2GraphStateProjector() {}

    public static void syncRuntimeState(RuntimeV2State runtimeState, OverAllState state) {
        syncRuntimeState(runtimeState, state, null, null, null);
    }

    public static void syncRuntimeState(
            RuntimeV2State runtimeState,
            OverAllState state,
            RuntimeV2Phase fallbackPhase,
            Map<String, Object> codeStateOverride,
            List<Map<String, Object>> observationTraceOverride) {
        if (runtimeState == null || state == null) {
            return;
        }
        runtimeState.setMode(resolveMode(state.value(RuntimeV2GraphStateKeys.MODE, "")));
        runtimeState.setFinishReason(resolveFinishReason(state.value(RuntimeV2GraphStateKeys.FINISH_REASON, "")));
        runtimeState.replaceMessages(state.<List<Message>>value(RuntimeV2GraphStateKeys.MESSAGES).orElse(List.of()));
        runtimeState.setFinalAnswer(resolveFinalAnswer(state));
        RuntimeV2Phase phase = resolvePhase(state.value(RuntimeV2GraphStateKeys.PHASE, ""));
        runtimeState.setPhase(phase == null ? fallbackPhase : phase);
        runtimeState.setIterationCount(state.value(RuntimeV2GraphStateKeys.ITERATION_COUNT, runtimeState.iterationCount()));
        runtimeState.setLlmCallCount(state.value(RuntimeV2GraphStateKeys.LLM_CALL_COUNT, runtimeState.llmCallCount()));
        runtimeState.setToolCallCount(state.value(RuntimeV2GraphStateKeys.TOOL_CALL_COUNT, runtimeState.toolCallCount()));
        runtimeState.setCodeState(codeStateOverride != null
                ? codeStateOverride
                : state.<Map<String, Object>>value(RuntimeV2GraphStateKeys.CODE_STATE).orElse(Map.of()));
        runtimeState.replaceObservationTrace(observationTraceOverride != null
                ? observationTraceOverride
                : state.<List<Map<String, Object>>>value(RuntimeV2GraphStateKeys.OBSERVATION_TRACE)
                        .orElse(List.of()));
    }

    private static String resolveFinalAnswer(OverAllState state) {
        String finalAnswer = state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER, "");
        if (!StringUtils.hasText(finalAnswer)) {
            finalAnswer = state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "");
        }
        return StringUtils.hasText(finalAnswer) ? finalAnswer : "";
    }

    private static RuntimeV2Mode resolveMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return RuntimeV2Mode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static RuntimeV2Phase resolvePhase(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return RuntimeV2Phase.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static RuntimeV2FinishReason resolveFinishReason(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return RuntimeV2FinishReason.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
