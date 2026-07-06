package lingzhou.agent.backend.capability.agentruntime.v2.graph.edge;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.cloud.ai.graph.OverAllState;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.graph.state.RuntimeV2GraphStateKeys;
import org.junit.jupiter.api.Test;

class RuntimeV2ObservationDispatcherTest {

    private final RuntimeV2ObservationDispatcher dispatcher = new RuntimeV2ObservationDispatcher();

    @Test
    void shouldRouteToLimitExceededWhenObservationStateTerminatesLoop() {
        OverAllState state = new OverAllState(Map.of(
                RuntimeV2GraphStateKeys.ITERATION_COUNT, 2,
                RuntimeV2GraphStateKeys.MAX_ITERATIONS, 6,
                RuntimeV2GraphStateKeys.OBSERVATION_STATE, Map.of("loopTerminated", true)));

        String next = dispatcher.apply(state);

        assertThat(next).isEqualTo(RuntimeV2GraphStateKeys.LIMIT_EXCEEDED_NODE);
    }

    @Test
    void shouldFollowRouteWhenObservationStateDoesNotTerminateLoop() {
        OverAllState state = new OverAllState(Map.of(
                RuntimeV2GraphStateKeys.ITERATION_COUNT,
                2,
                RuntimeV2GraphStateKeys.MAX_ITERATIONS,
                6,
                RuntimeV2GraphStateKeys.ROUTE,
                RuntimeV2GraphStateKeys.REASONING_NODE,
                RuntimeV2GraphStateKeys.OBSERVATION_STATE,
                Map.of("shouldSummarize", true, "messagePressure", true)));

        String next = dispatcher.apply(state);

        assertThat(next).isEqualTo(RuntimeV2GraphStateKeys.REASONING_NODE);
    }
}
