package lingzhou.agent.backend.capability.agentruntime.v2.engine;

import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface RuntimeV2ExecutionEngine {

    RuntimeV2EngineType engineType();

    Flux<ServerSentEvent<String>> stream(ChatRuntimePreparedRequest prepared, Long userId);

    default boolean requestCancellation(String runCode, Long userId, String reason) {
        return false;
    }
}
