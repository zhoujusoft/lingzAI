package lingzhou.agent.backend.capability.agentruntime.v2.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

class RuntimeV2ExecutionGatewayTest {

    @Test
    void shouldUseConfiguredGraphEngineByDefault() {
        RecordingEngine graphEngine = new RecordingEngine(RuntimeV2EngineType.GRAPH, Flux.just(event("graph")));
        RuntimeV2ExecutionGateway gateway = new RuntimeV2ExecutionGateway(List.of(graphEngine), "graph");

        List<ServerSentEvent<String>> events =
                gateway.stream(prepared("{}"), 1L).collectList().block();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("graph");
        assertThat(graphEngine.callCount()).isEqualTo(1);
    }

    @Test
    void shouldRunWithOnlyGraphEngineRegistered() {
        RecordingEngine graphEngine = new RecordingEngine(RuntimeV2EngineType.GRAPH, Flux.just(event("graph")));
        RuntimeV2ExecutionGateway gateway = new RuntimeV2ExecutionGateway(List.of(graphEngine), "classic");

        List<ServerSentEvent<String>> events = gateway.stream(prepared("{\"runtimeV2Engine\":\"classic\"}"), 1L)
                .collectList()
                .block();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("graph");
        assertThat(graphEngine.callCount()).isEqualTo(1);
    }

    @Test
    void shouldSurfaceGraphFailureWithoutClassicFallback() {
        RecordingEngine graphEngine =
                new RecordingEngine(RuntimeV2EngineType.GRAPH, Flux.error(new IllegalStateException("graph failed")));
        RuntimeV2ExecutionGateway gateway = new RuntimeV2ExecutionGateway(List.of(graphEngine), "graph");

        assertThatThrownBy(
                        () -> gateway.stream(prepared("{}"), 1L).collectList().block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("graph failed");

        assertThat(graphEngine.callCount()).isEqualTo(1);
    }

    @Test
    void shouldIgnorePerRequestClassicOverride() {
        RecordingEngine graphEngine = new RecordingEngine(RuntimeV2EngineType.GRAPH, Flux.just(event("graph")));
        RuntimeV2ExecutionGateway gateway = new RuntimeV2ExecutionGateway(List.of(graphEngine), "graph");

        List<ServerSentEvent<String>> events = gateway.stream(prepared("{\"runtimeV2Engine\":\"classic\"}"), 1L)
                .collectList()
                .block();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("graph");
        assertThat(graphEngine.callCount()).isEqualTo(1);
    }

    private ChatRuntimePreparedRequest prepared(String paramsJson) {
        return new ChatRuntimePreparedRequest(
                ConversationSessionType.GENERAL_CHAT_V2,
                LingzRuntimeScopeType.GENERAL,
                "session-id",
                null,
                null,
                "message",
                "message",
                "normal",
                "GENERAL_CHAT_V2",
                paramsJson,
                null,
                List.of(),
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                false,
                "");
    }

    private ServerSentEvent<String> event(String data) {
        return ServerSentEvent.<String>builder().data(data).build();
    }

    private static final class RecordingEngine implements RuntimeV2ExecutionEngine {

        private final RuntimeV2EngineType engineType;
        private final Flux<ServerSentEvent<String>> response;
        private final AtomicInteger callCount = new AtomicInteger();

        private RecordingEngine(RuntimeV2EngineType engineType, Flux<ServerSentEvent<String>> response) {
            this.engineType = engineType;
            this.response = response;
        }

        @Override
        public RuntimeV2EngineType engineType() {
            return engineType;
        }

        @Override
        public Flux<ServerSentEvent<String>> stream(ChatRuntimePreparedRequest prepared, Long userId) {
            callCount.incrementAndGet();
            return response;
        }

        private int callCount() {
            return callCount.get();
        }
    }
}
