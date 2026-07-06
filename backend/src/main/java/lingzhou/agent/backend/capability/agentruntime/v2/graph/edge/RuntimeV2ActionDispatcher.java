package lingzhou.agent.backend.capability.agentruntime.v2.graph.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import org.springframework.util.StringUtils;

public class RuntimeV2ActionDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
        String finishReason = state.value(RuntimeV2GraphStateKeys.FINISH_REASON, "");
        if (RuntimeV2FinishReason.WAITING_APPROVAL.name().equalsIgnoreCase(finishReason)) {
            return RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE;
        }
        String route = state.value(RuntimeV2GraphStateKeys.ROUTE, "");
        if (!StringUtils.hasText(route)) {
            return RuntimeV2GraphStateKeys.OBSERVATION_NODE;
        }
        if (StateGraph.END.equals(route)) {
            return StateGraph.END;
        }
        return route;
    }
}
