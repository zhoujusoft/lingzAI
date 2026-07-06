package lingzhou.agent.backend.capability.agentruntime.v2.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2UsageGuardSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Phase;
import org.springframework.util.StringUtils;

public class RuntimeV2LimitExceededNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> output = new LinkedHashMap<>();
        String finalMessage = state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "");
        if (!StringUtils.hasText(finalMessage)) {
            String runtimeStatus = state.value(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS, "");
            if (RuntimeV2UsageGuardSupport.TOKEN_BUDGET_EXCEEDED_STATUS.equalsIgnoreCase(runtimeStatus)) {
                finalMessage = RuntimeV2UsageGuardSupport.TOKEN_BUDGET_EXCEEDED_MESSAGE;
            } else if (RuntimeV2UsageGuardSupport.OBSERVATION_LOOP_DETECTED_STATUS.equalsIgnoreCase(runtimeStatus)) {
                finalMessage = RuntimeV2UsageGuardSupport.OBSERVATION_LOOP_DETECTED_MESSAGE;
            } else {
                finalMessage = "本次运行已触发保护机制，请收敛问题后重试。";
            }
        }
        output.put(RuntimeV2GraphStateKeys.PHASE, RuntimeV2Phase.FINALIZING.name());
        output.put(RuntimeV2GraphStateKeys.FINISH_REASON, RuntimeV2FinishReason.LIMIT_EXCEEDED.name());
        output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, finalMessage);
        output.put(RuntimeV2GraphStateKeys.ROUTE, RuntimeV2GraphStateKeys.FINAL_ANSWER_NODE);
        return output;
    }
}
