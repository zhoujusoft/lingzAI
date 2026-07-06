package lingzhou.agent.backend.capability.agentruntime.v2.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

class RuntimeV2GraphEventProjectorTest {

    @Test
    void shouldProjectToolCallStartedEventToExistingSseContract() {
        RuntimeV2GraphEvent event = RuntimeV2GraphEvent.toolCallStarted(Map.of(
                "id", "tool-1",
                "name", "search_web",
                "arguments", "{\"query\":\"agent runtime\"}"));

        ServerSentEvent<String> sse = RuntimeV2GraphEventProjector.toServerSentEvent(event);

        assertThat(sse).isNotNull();
        assertThat(sse.event()).isEqualTo("tool_call_started");
        assertThat(JSON.parseObject(sse.data()))
                .isEqualTo(Map.of(
                        "type",
                        "tool",
                        "content",
                        Map.of(
                                "id", "tool-1",
                                "name", "search_web",
                                "arguments", "{\"query\":\"agent runtime\"}")));
    }

    @Test
    void shouldProjectPhaseEventToExistingSseContract() {
        RuntimeV2GraphEvent event = RuntimeV2GraphEvent.phase(Map.of(
                "phase", "ACTION",
                "mode", "TASK"));

        ServerSentEvent<String> sse = RuntimeV2GraphEventProjector.toServerSentEvent(event);

        assertThat(sse).isNotNull();
        assertThat(sse.event()).isEqualTo("phase");
        assertThat(JSON.parseObject(sse.data()))
                .isEqualTo(Map.of(
                        "type",
                        "phase-progress",
                        "content",
                        Map.of(
                                "phase", "ACTION",
                                "mode", "TASK")));
    }
}
