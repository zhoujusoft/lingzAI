package lingzhou.agent.backend.capability.agentruntime.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class RuntimeTokenUsageAccumulatorTests {

    @Test
    void shouldAggregateUsageAcrossCallsAndTools() {
        RuntimeTokenUsageAccumulator accumulator =
                new RuntimeTokenUsageAccumulator(1_000L, 101L, "OPENAI", "gpt-test", "VLLM");

        accumulator.ensureCurrentCall(1_000L);
        accumulator.recordToolEvent("tool", "{\"content\":{\"id\":\"tool-1\",\"name\":\"search\"}}");
        accumulator.recordResponse(chatResponse("第一轮", 10, 5, 15));
        accumulator.completeCurrentCall("COMPLETED", 1_200L, 32);

        accumulator.ensureCurrentCall(1_300L);
        accumulator.recordToolEvent("tool", "{\"content\":{\"id\":\"tool-2\",\"name\":\"python\"}}");
        accumulator.recordResponse(chatResponse("第二轮", 8, 4, null));
        accumulator.completeCurrentCall("FAILED", 1_450L, 18);

        RuntimeRunUsageSnapshot snapshot = accumulator.snapshot("FAILED", 1_500L);

        assertThat(snapshot.runStatus()).isEqualTo("FAILED");
        assertThat(snapshot.usageAvailable()).isTrue();
        assertThat(snapshot.promptTokens()).isEqualTo(18);
        assertThat(snapshot.completionTokens()).isEqualTo(9);
        assertThat(snapshot.totalTokens()).isEqualTo(27);
        assertThat(snapshot.llmCallCount()).isEqualTo(2);
        assertThat(snapshot.toolCallCount()).isEqualTo(2);
        assertThat(snapshot.durationMs()).isEqualTo(500L);
        assertThat(snapshot.modelCalls()).hasSize(2);
        assertThat(snapshot.modelCalls().get(0).status()).isEqualTo("COMPLETED");
        assertThat(snapshot.modelCalls().get(1).totalTokens()).isEqualTo(12);
    }

    @Test
    void shouldMarkUsageUnavailableWhenProviderUsageMissing() {
        RuntimeTokenUsageAccumulator accumulator =
                new RuntimeTokenUsageAccumulator(2_000L, 102L, "OPENAI", "gpt-test", "VLLM");

        accumulator.ensureCurrentCall(2_000L);
        accumulator.completeCurrentCall("CANCELLED", 2_120L, 0);

        RuntimeRunUsageSnapshot snapshot = accumulator.snapshot("CANCELLED", 2_120L);

        assertThat(snapshot.usageAvailable()).isFalse();
        assertThat(snapshot.promptTokens()).isNull();
        assertThat(snapshot.completionTokens()).isNull();
        assertThat(snapshot.totalTokens()).isNull();
        assertThat(snapshot.llmCallCount()).isEqualTo(1);
        assertThat(snapshot.toolCallCount()).isZero();
        assertThat(snapshot.modelCalls())
                .extracting(RuntimeModelCallUsage::usageAvailable)
                .containsExactly(false);
    }

    @Test
    void shouldIgnoreTrailingUsageOnlyChunkAfterCallCompleted() {
        RuntimeTokenUsageAccumulator accumulator =
                new RuntimeTokenUsageAccumulator(3_000L, 103L, "OPENAI", "gpt-test", "VLLM");

        accumulator.ensureCurrentCall(3_000L);
        accumulator.recordResponse(chatResponse("首轮输出", 12, 6, 18));
        accumulator.completeCurrentCall("COMPLETED", 3_180L, 24);

        accumulator.recordResponse(chatResponse("", 12, 6, 18));

        RuntimeRunUsageSnapshot snapshot = accumulator.snapshot("COMPLETED", 3_200L);

        assertThat(snapshot.llmCallCount()).isEqualTo(1);
        assertThat(snapshot.promptTokens()).isEqualTo(12);
        assertThat(snapshot.completionTokens()).isEqualTo(6);
        assertThat(snapshot.totalTokens()).isEqualTo(18);
        assertThat(accumulator.hasCurrentCall()).isFalse();
    }

    private ChatResponse chatResponse(
            String text, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder()
                        .model("gpt-test")
                        .usage(new DefaultUsage(promptTokens, completionTokens, totalTokens))
                        .build());
    }
}
