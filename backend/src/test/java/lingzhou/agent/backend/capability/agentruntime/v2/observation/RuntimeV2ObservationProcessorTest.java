package lingzhou.agent.backend.capability.agentruntime.v2.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeV2ObservationProcessorTest {

    private final RuntimeV2ObservationProcessor processor = new RuntimeV2ObservationProcessor();

    @Test
    void shouldTerminateLoopAfterThreeIdenticalObservations() {
        Map<String, Object> state = processor.evaluate(
                List.of(Map.of("observation", "same"), Map.of("observation", "same"), Map.of("observation", "same")));

        assertThat(state.get("duplicateObservation")).isEqualTo(Boolean.TRUE);
        assertThat(state.get("loopTerminated")).isEqualTo(Boolean.TRUE);
        assertThat(state.get("error")).isEqualTo("连续 3 次 observation 相同，已强制终止图循环");
    }

    @Test
    void shouldSuggestSummarizingWhenMessageChainGrows() {
        Map<String, Object> state = processor.evaluate(
                List.of(
                        Map.of("observation", "obs-1"),
                        Map.of("observation", "obs-2"),
                        Map.of("observation", "obs-3"),
                        Map.of("observation", "obs-4"),
                        Map.of("observation", "obs-5"),
                        Map.of("observation", "obs-6")),
                24);

        assertThat(state.get("shouldSummarize")).isEqualTo(Boolean.TRUE);
        assertThat(state.get("summaryReason")).isEqualTo("message-pressure");
        assertThat(state.get("messageCount")).isEqualTo(24);
        assertThat(state.get("loopTerminated")).isEqualTo(Boolean.FALSE);
    }

    @Test
    void shouldNotSuggestSummarizingWhenMessageChainIsShort() {
        Map<String, Object> state = processor.evaluate(
                List.of(
                        Map.of("observation", "obs-1"),
                        Map.of("observation", "obs-2"),
                        Map.of("observation", "obs-3"),
                        Map.of("observation", "obs-4"),
                        Map.of("observation", "obs-5"),
                        Map.of("observation", "obs-6")),
                8);

        assertThat(state.get("shouldSummarize")).isEqualTo(Boolean.FALSE);
        assertThat(state.get("summaryReason")).isEqualTo("");
    }
}
