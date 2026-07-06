package lingzhou.agent.backend.capability.agentruntime.v2.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.LingzRuntimeScopeType;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import lingzhou.agent.backend.capability.modelruntime.ModelRuntimeConfigResolver;
import org.junit.jupiter.api.Test;

class RuntimeV2GraphEngineRunUsageSnapshotTest {

    @Test
    void shouldBuildRunUsageSnapshotFromGraphRuntimeState() {
        RuntimeV2State state = new RuntimeV2State(
                new ChatRuntimePreparedRequest(
                        ConversationSessionType.GENERAL_CHAT_V2,
                        LingzRuntimeScopeType.GENERAL,
                        "session-1",
                        null,
                        null,
                        "message",
                        "message",
                        "normal",
                        "GENERAL_CHAT_V2",
                        "{}",
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        12L,
                        false,
                        ""),
                7L,
                null,
                List.of(),
                null,
                new ModelRuntimeConfigResolver.ResolvedChatModelConfig(
                        "db",
                        "OPENAI",
                        "openai",
                        "GPT-4.1",
                        12L,
                        "https://api.openai.com",
                        "sk-ignored",
                        "/v1/chat/completions",
                        "gpt-4.1",
                        0.2,
                        8192,
                        null,
                        false));
        state.incrementLlmCallCount();
        state.incrementLlmCallCount();
        state.incrementToolCallCount();
        state.addUsage(11, 22, 33);

        RuntimeRunUsageSnapshot snapshot = RuntimeV2GraphEngine.buildRunUsageSnapshot(state, "SUCCEEDED", 1000L, 1800L);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.runStatus()).isEqualTo("SUCCEEDED");
        assertThat(snapshot.usageAvailable()).isTrue();
        assertThat(snapshot.promptTokens()).isEqualTo(11);
        assertThat(snapshot.completionTokens()).isEqualTo(22);
        assertThat(snapshot.totalTokens()).isEqualTo(33);
        assertThat(snapshot.llmCallCount()).isEqualTo(2);
        assertThat(snapshot.toolCallCount()).isEqualTo(1);
        assertThat(snapshot.durationMs()).isEqualTo(800L);
        assertThat(snapshot.modelId()).isEqualTo(12L);
        assertThat(snapshot.modelProvider()).isEqualTo("openai");
        assertThat(snapshot.modelName()).isEqualTo("gpt-4.1");
    }
}
