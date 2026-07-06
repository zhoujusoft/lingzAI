package lingzhou.agent.backend.capability.agentruntime.v2.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2FinishReason;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Phase;
import org.springframework.util.StringUtils;

public class RuntimeV2FinalAnswerNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> output = new LinkedHashMap<>();
        String finalAnswer = state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER, "");
        if (!StringUtils.hasText(finalAnswer)) {
            finalAnswer = state.value(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "");
        }
        if (!StringUtils.hasText(finalAnswer)) {
            String runtimeStatus = state.value(RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS, "");
            if (StringUtils.hasText(runtimeStatus)) {
                finalAnswer = "Runtime V2 graph 运行时未就绪：" + runtimeStatus;
            } else {
                finalAnswer = "Runtime V2 graph 未生成最终回答。";
            }
        }
        output.put(RuntimeV2GraphStateKeys.PHASE, RuntimeV2Phase.COMPLETED.name());
        output.put(RuntimeV2GraphStateKeys.FINAL_ANSWER, finalAnswer);
        output.put(
                RuntimeV2GraphStateKeys.FINISH_REASON,
                state.value(RuntimeV2GraphStateKeys.FINISH_REASON, RuntimeV2FinishReason.COMPLETED.name()));
        return output;
    }
}
