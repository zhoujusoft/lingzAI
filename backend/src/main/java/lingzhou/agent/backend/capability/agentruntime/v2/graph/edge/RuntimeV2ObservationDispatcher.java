package lingzhou.agent.backend.capability.agentruntime.v2.graph.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import org.springframework.util.StringUtils;

public class RuntimeV2ObservationDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
        String finishReason = state.value(RuntimeV2GraphStateKeys.FINISH_REASON, "");
        if (RuntimeV2FinishReason.LIMIT_EXCEEDED.name().equalsIgnoreCase(finishReason)) {
            return RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE;
        }
        Map<String, Object> observationState = state.<Map<String, Object>>value(
                        RuntimeV2GraphStateKeys.OBSERVATION_STATE)
                .orElse(Map.of());
        Object loopTerminated = observationState.get("loopTerminated");
        if (loopTerminated != null
                && "true".equalsIgnoreCase(String.valueOf(loopTerminated).trim())) {
            return RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE;
        }
        String route = state.value(RuntimeV2GraphStateKeys.ROUTE, "");
        if (!StringUtils.hasText(route)) {
            return RuntimeV2GraphStateKeys.REASONING_NODE;
        }
        if (StateGraph.END.equals(route)) {
            return StateGraph.END;
        }
        return route;
    }
}
