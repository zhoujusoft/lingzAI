package lingzhou.agent.backend.capability.agentruntime.context;

import java.util.List;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.capability.agentruntime.prompt.RuntimePromptPack;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

public record RuntimeContextAssembly(
        List<Message> historyMessages, String systemPrompt, String userMessage, List<ToolCallback> toolCallbacks) {

    public RuntimeContextAssembly {
        historyMessages = historyMessages == null ? List.of() : List.copyOf(historyMessages);
        toolCallbacks = toolCallbacks == null ? List.of() : List.copyOf(toolCallbacks);
    }

    public static RuntimeContextAssembly empty() {
        return new RuntimeContextAssembly(List.of(), null, null, List.of());
    }

    public static RuntimeContextAssembly fromPrepared(
            ChatRuntimePreparedRequest prepared, RuntimePromptPack promptPack, List<Message> historyMessages) {
        if (prepared == null) {
            return new RuntimeContextAssembly(historyMessages, null, null, List.of());
        }
        return new RuntimeContextAssembly(
                historyMessages,
                promptPack == null ? null : promptPack.systemPrompt(),
                prepared.userMessage(),
                prepared.toolCallbacks());
    }
}
