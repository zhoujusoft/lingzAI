package lingzhou.agent.backend.capability.agentruntime.v2.graph;

import java.util.Map;
import java.util.function.Consumer;
import lingzhou.agent.backend.capability.agentruntime.v2.code.RuntimeV2CodeStageRuntime;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

public record RuntimeV2GraphExecutionContext(
        RuntimeV2State runtimeState,
        ChatClient streamChatClient,
        ChatClient decisionChatClient,
        Map<String, ToolCallback> toolCallbackIndex,
        RuntimeV2CodeStageRuntime codeStageRuntime,
        Consumer<RuntimeV2GraphEvent> eventEmitter) {}
