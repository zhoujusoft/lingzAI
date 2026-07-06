package lingzhou.agent.backend.capability.agentruntime.v2.graph.node;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.cloud.ai.graph.OverAllState;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.RuntimeV2UsageGuardSupport;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import org.junit.jupiter.api.Test;

class RuntimeV2LimitExceededNodeTest {

    private final RuntimeV2LimitExceededNode node = new RuntimeV2LimitExceededNode();

    @Test
    void shouldPreferExistingDraftMessage() {
        Map<String, Object> output =
                node.apply(new OverAllState(Map.of(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT, "自定义停止提示")));

        assertThat(output.get(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT)).isEqualTo("自定义停止提示");
    }

    @Test
    void shouldUseTokenBudgetMessageWhenBudgetExceeded() {
        Map<String, Object> output = node.apply(new OverAllState(Map.of(
                RuntimeV2GraphStateKeys.GRAPH_RUNTIME_STATUS,
                RuntimeV2UsageGuardSupport.TOKEN_BUDGET_EXCEEDED_STATUS)));

        assertThat(output.get(RuntimeV2GraphStateKeys.FINAL_ANSWER_DRAFT))
                .isEqualTo(RuntimeV2UsageGuardSupport.TOKEN_BUDGET_EXCEEDED_MESSAGE);
    }
}
