package lingzhou.agent.backend.capability.agentruntime.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeRunUsageSnapshotMergerTest {

    @Test
    void shouldMergeMultipleSnapshotsIntoSingleUsageView() {
        RuntimeRunUsageSnapshot first = new RuntimeRunUsageSnapshot(
                "COMPLETED",
                true,
                100,
                20,
                120,
                1,
                0,
                1000L,
                1000L,
                2000L,
                1L,
                "qwen-online",
                "qwen3-max",
                "QWEN_ONLINE",
                List.of(new RuntimeModelCallUsage(1, "COMPLETED", true, 100, 20, 120, 10, 1000L, 2000L, 1000L)));
        RuntimeRunUsageSnapshot second = new RuntimeRunUsageSnapshot(
                "COMPLETED",
                true,
                30,
                10,
                40,
                1,
                2,
                500L,
                2500L,
                3000L,
                1L,
                "qwen-online",
                "qwen3-max",
                "QWEN_ONLINE",
                List.of(new RuntimeModelCallUsage(1, "COMPLETED", true, 30, 10, 40, 5, 2500L, 3000L, 500L)));
        RuntimeRunUsageSnapshot third = new RuntimeRunUsageSnapshot(
                "COMPLETED",
                true,
                50,
                15,
                65,
                1,
                5,
                700L,
                3100L,
                3800L,
                1L,
                "qwen-online",
                "qwen3-max",
                "QWEN_ONLINE",
                List.of(new RuntimeModelCallUsage(1, "COMPLETED", true, 50, 15, 65, 6, 3100L, 3800L, 700L)));

        RuntimeRunUsageSnapshot merged = RuntimeRunUsageSnapshotMerger.merge(List.of(first, second, third));

        assertThat(merged).isNotNull();
        assertThat(merged.promptTokens()).isEqualTo(180);
        assertThat(merged.completionTokens()).isEqualTo(45);
        assertThat(merged.totalTokens()).isEqualTo(225);
        assertThat(merged.llmCallCount()).isEqualTo(3);
        assertThat(merged.toolCallCount()).isEqualTo(7);
        assertThat(merged.modelCalls()).hasSize(3);
        assertThat(merged.modelCalls().get(0).callNo()).isEqualTo(1);
        assertThat(merged.modelCalls().get(1).callNo()).isEqualTo(2);
        assertThat(merged.modelCalls().get(2).callNo()).isEqualTo(3);
        assertThat(merged.startedAtMillis()).isEqualTo(1000L);
        assertThat(merged.completedAtMillis()).isEqualTo(3800L);
        assertThat(merged.durationMs()).isEqualTo(2800L);
    }
}
