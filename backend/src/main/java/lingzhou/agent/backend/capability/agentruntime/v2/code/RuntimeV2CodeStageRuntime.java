package lingzhou.agent.backend.capability.agentruntime.v2.code;

import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.ai.chat.client.ChatClient;

public record RuntimeV2CodeStageRuntime(RuntimeV2State state, ChatClient chatClient) {}
