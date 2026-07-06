package lingzhou.agent.backend.capability.agentruntime.v2.graph.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import org.springframework.util.StringUtils;

public class RuntimeV2ReasoningDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
        String finishReason = state.value(RuntimeV2GraphStateKeys.FINISH_REASON, "");
        if (RuntimeV2FinishReason.LIMIT_EXCEEDED.name().equalsIgnoreCase(finishReason)) {
            return RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE;
        }
        boolean continueReasoning = state.value(RuntimeV2GraphStateKeys.CONTINUE_REASONING, false);
        if (continueReasoning) {
            return RuntimeV2GraphStateKeys.REASONING_NODE;
        }
        String finalAnswer = state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER, "");
        String finalDraft = state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "");
        boolean needsToolCall = state.value(RuntimeV2GraphStateKeys.NEEDS_TOOL_CALL, false);
        boolean needsCodeEscalation = state.value(RuntimeV2GraphStateKeys.NEEDS_CODE_ESCALATION, false);
        if (!needsToolCall
                && !needsCodeEscalation
                && (StringUtils.hasText(finalAnswer) || StringUtils.hasText(finalDraft))) {
            return RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE;
        }
        if (needsCodeEscalation) {
            return RuntimeV2GraphStateKeys.CODE_ESCALATION_NODE;
        }
        if (needsToolCall) {
            return RuntimeV2GraphStateKeys.ACTION_NODE;
        }
        String route = state.value(RuntimeV2GraphStateKeys.ROUTE, "");
        if (!StringUtils.hasText(route)) {
            return RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE;
        }
        if (StateGraph.END.equals(route)) {
            return StateGraph.END;
        }
        return route;
    }
}
