package lingzhou.agent.backend.capability.agentruntime.v2.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Mode;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Phase;
import org.springframework.util.StringUtils;

public class RuntimeV2TriageNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> output = new LinkedHashMap<>();
        String mode = state.value(RuntimeV2GraphStateKeys.MODE, RuntimeV2Mode.DIRECT.name());
        String executionModeHint = state.value(RuntimeV2GraphStateKeys.EXECUTION_MODE_HINT, "");

        if (!StringUtils.hasText(mode)) {
            mode = "TOOL".equalsIgnoreCase(executionModeHint)
                    ? RuntimeV2Mode.REACT.name()
                    : RuntimeV2Mode.DIRECT.name();
        }

        output.put(RuntimeV2GraphStateKeys.MODE, mode);
        output.put(RuntimeV2GraphStateKeys.PHASE, RuntimeV2Phase.TRIAGE.name());
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.REASONING_NODE);
        output.put(
                RuntimeV2GraphStateKeys.WORKING_MEMORY,
                Map.of(
                        "goal",
                        state.value(RuntimeV2GraphStateKeys.USER_REQUEST, ""),
                        "executionModeHint",
                        executionModeHint));
        return output;
    }
}
