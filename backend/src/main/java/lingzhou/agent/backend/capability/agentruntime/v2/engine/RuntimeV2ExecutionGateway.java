package lingzhou.agent.backend.capability.agentruntime.v2.engine;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class RuntimeV2ExecutionGateway {

    private final Map<RuntimeV2EngineType, RuntimeV2ExecutionEngine> engineIndex;
    private final RuntimeV2EngineType configuredEngineType;

    public RuntimeV2ExecutionGateway(
            List<RuntimeV2ExecutionEngine> engines,
            @Value("${app.agent.runtime-v2.engine:graph}") String configuredEngine) {
        this.engineIndex = new EnumMap<>(RuntimeV2EngineType.class);
        if (engines != null) {
            for (RuntimeV2ExecutionEngine engine : engines) {
                if (engine != null) {
                    engineIndex.put(engine.engineType(), engine);
                }
            }
        }
        this.configuredEngineType = RuntimeV2EngineType.fromConfigValue(configuredEngine);
        if (StringUtils.hasText(configuredEngine) && !isGraphCompatibleValue(configuredEngine)) {
            log.warn("Runtime V2 legacy 引擎配置已忽略，统一切换 graph：configuredEngine={}", configuredEngine);
        }
    }

    public Flux<ServerSentEvent<String>> stream(ChatRuntimePreparedRequest prepared, Long userId) {
        RuntimeV2EngineType selectedType = configuredEngineType;
        RuntimeV2ExecutionEngine selectedEngine = engineIndex.get(selectedType);
        if (selectedEngine == null) {
            throw new IllegalStateException("Runtime V2 engine 未注册：" + selectedType.configValue());
        }
        return Flux.defer(() -> selectedEngine.stream(prepared, userId))
                .doOnSubscribe(ignored -> log.info(
                        "Runtime V2 引擎执行：engine={}, sessionId={}",
                        selectedType.configValue(),
                        resolveSessionLogValue(prepared)));
    }

    public boolean requestCancellation(String runCode, Long userId, String reason) {
        RuntimeV2ExecutionEngine selectedEngine = engineIndex.get(configuredEngineType);
        if (selectedEngine == null) {
            throw new IllegalStateException("Runtime V2 engine 未注册：" + configuredEngineType.configValue());
        }
        return selectedEngine.requestCancellation(runCode, userId, reason);
    }

    private boolean isGraphCompatibleValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return true;
        }
        String normalized = rawValue.trim();
        return "graph".equalsIgnoreCase(normalized) || "graph-preview".equalsIgnoreCase(normalized);
    }

    private String resolveSessionLogValue(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || !StringUtils.hasText(prepared.sessionId())) {
            return "new-session";
        }
        return prepared.sessionId().trim();
    }
}
