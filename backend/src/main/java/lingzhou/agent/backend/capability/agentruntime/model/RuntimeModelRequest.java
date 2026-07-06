package lingzhou.agent.backend.capability.agentruntime.model;

import java.util.Objects;
import lingzhou.agent.backend.capability.agentruntime.context.RuntimeContextAssembly;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.StringUtils;

public record RuntimeModelRequest(
        ModelRuntimeClientFactory.ChatRuntimeBundle chatRuntimeBundle,
        ChatClient.ChatClientRequestSpec requestSpec,
        RuntimeContextAssembly contextAssembly) {

    public RuntimeModelRequest {
        Objects.requireNonNull(chatRuntimeBundle, "chatRuntimeBundle");
        Objects.requireNonNull(requestSpec, "requestSpec");
        Objects.requireNonNull(contextAssembly, "contextAssembly");
    }

    public RuntimeModelRequest withAdditionalSystemPrompt(String additionalSystemPrompt) {
        if (!StringUtils.hasText(additionalSystemPrompt)) {
            return this;
        }
        String nextSystemPrompt = mergeSystemPrompt(contextAssembly.systemPrompt(), additionalSystemPrompt);
        RuntimeContextAssembly nextContextAssembly = new RuntimeContextAssembly(
                contextAssembly.historyMessages(),
                nextSystemPrompt,
                contextAssembly.userMessage(),
                contextAssembly.toolCallbacks());
        return new RuntimeModelRequest(chatRuntimeBundle, buildRequestSpec(nextContextAssembly), nextContextAssembly);
    }

    private ChatClient.ChatClientRequestSpec buildRequestSpec(RuntimeContextAssembly assembly) {
        ChatClient.ChatClientRequestSpec spec = chatRuntimeBundle.chatClient().prompt();
        if (assembly == null) {
            return spec;
        }
        if (!assembly.historyMessages().isEmpty()) {
            spec = spec.messages(assembly.historyMessages());
        }
        if (StringUtils.hasText(assembly.systemPrompt())) {
            spec.system(assembly.systemPrompt());
        }
        if (StringUtils.hasText(assembly.userMessage())) {
            spec.user(assembly.userMessage());
        }
        if (!assembly.toolCallbacks().isEmpty()) {
            spec.toolCallbacks(assembly.toolCallbacks());
        }
        return spec;
    }

    private String mergeSystemPrompt(String existingSystemPrompt, String additionalSystemPrompt) {
        if (!StringUtils.hasText(existingSystemPrompt)) {
            return additionalSystemPrompt.trim();
        }
        return existingSystemPrompt.trim() + "\n\n" + additionalSystemPrompt.trim();
    }
}
