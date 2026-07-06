package lingzhou.agent.backend.capability.agentruntime.v2.graph.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import org.springframework.util.StringUtils;

public class RuntimeV2TriageDispatcher implements EdgeAction {

    @Override
    public String apply(OverAllState state) {
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
