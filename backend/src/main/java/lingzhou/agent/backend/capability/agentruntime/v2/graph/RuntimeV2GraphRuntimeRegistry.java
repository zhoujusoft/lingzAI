package lingzhou.agent.backend.capability.agentruntime.v2.graph;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeStageRuntime;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class RuntimeV2GraphRuntimeRegistry {

    private final Map<String, RuntimeContext> registry = new ConcurrentHashMap<>();

    public void register(
            String runtimeContextKey,
            Map<String, ToolCallback> toolCallbackIndex,
            RuntimeV2CodeStageRuntime codeStageRuntime) {
        register(runtimeContextKey, null, null, null, toolCallbackIndex, codeStageRuntime, null);
    }

    public void register(
            String runtimeContextKey,
            RuntimeV2State runtimeState,
            ChatClient streamChatClient,
            ChatClient decisionChatClient,
            Map<String, ToolCallback> toolCallbackIndex,
            RuntimeV2CodeStageRuntime codeStageRuntime) {
        register(
                runtimeContextKey,
                runtimeState,
                streamChatClient,
                decisionChatClient,
                toolCallbackIndex,
                codeStageRuntime,
                null);
    }

    public void register(
            String runtimeContextKey,
            RuntimeV2State runtimeState,
            ChatClient streamChatClient,
            ChatClient decisionChatClient,
            Map<String, ToolCallback> toolCallbackIndex,
            RuntimeV2CodeStageRuntime codeStageRuntime,
            Consumer<RuntimeV2GraphEvent> eventEmitter) {
        if (runtimeContextKey == null || runtimeContextKey.isBlank()) {
            return;
        }
        registry.put(
                runtimeContextKey.trim(),
                new RuntimeContext(new RuntimeV2GraphExecutionContext(
                        runtimeState,
                        streamChatClient,
                        decisionChatClient,
                        toolCallbackIndex == null ? Map.of() : Map.copyOf(toolCallbackIndex),
                        codeStageRuntime,
                        eventEmitter)));
    }

    public Map<String, ToolCallback> resolveToolCallbackIndex(String runtimeContextKey) {
        RuntimeContext context = resolve(runtimeContextKey);
        return context == null ? Map.of() : context.executionContext().toolCallbackIndex();
    }

    public RuntimeV2CodeStageRuntime resolveCodeStageRuntime(String runtimeContextKey) {
        RuntimeContext context = resolve(runtimeContextKey);
        return context == null ? null : context.executionContext().codeStageRuntime();
    }

    public RuntimeV2GraphExecutionContext resolveExecutionContext(String runtimeContextKey) {
        RuntimeContext context = resolve(runtimeContextKey);
        return context == null ? null : context.executionContext();
    }

    public boolean requestCancellation(String runtimeContextKey, String reason) {
        RuntimeContext context = resolve(runtimeContextKey);
        if (context == null
                || context.executionContext() == null
                || context.executionContext().runtimeState() == null) {
            return false;
        }
        context.executionContext().runtimeState().requestCancellation(reason);
        return true;
    }

    public void replaceToolCallbackIndex(String runtimeContextKey, Map<String, ToolCallback> toolCallbackIndex) {
        RuntimeContext context = resolve(runtimeContextKey);
        if (context == null || context.executionContext() == null) {
            return;
        }
        RuntimeV2GraphExecutionContext existing = context.executionContext();
        registry.put(
                runtimeContextKey.trim(),
                new RuntimeContext(new RuntimeV2GraphExecutionContext(
                        existing.runtimeState(),
                        existing.streamChatClient(),
                        existing.decisionChatClient(),
                        toolCallbackIndex == null ? Map.of() : Map.copyOf(toolCallbackIndex),
                        existing.codeStageRuntime(),
                        existing.eventEmitter())));
    }

    public void unregister(String runtimeContextKey) {
        if (runtimeContextKey == null || runtimeContextKey.isBlank()) {
            return;
        }
        registry.remove(runtimeContextKey.trim());
    }

    private RuntimeContext resolve(String runtimeContextKey) {
        if (runtimeContextKey == null || runtimeContextKey.isBlank()) {
            return null;
        }
        return registry.get(runtimeContextKey.trim());
    }

    private record RuntimeContext(RuntimeV2GraphExecutionContext executionContext) {}
}
