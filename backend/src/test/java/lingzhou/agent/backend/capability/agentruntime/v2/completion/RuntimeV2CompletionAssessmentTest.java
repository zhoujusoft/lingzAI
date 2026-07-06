package lingzhou.agent.backend.capability.agentruntime.v2.completion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationEntry;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationStatus;
import org.junit.jupiter.api.Test;

class RuntimeV2CompletionAssessmentTest {

    @Test
    void shouldExposeSerializablePayloadOnly() {
        RuntimeV2CompletionAssessment assessment = new RuntimeV2CompletionAssessment(
                true,
                false,
                List.of(new RuntimeV2CompletionBlocker(
                        "artifact.missing",
                        "缺少产物",
                        RuntimeV2CompletionBlockerSource.RUNTIME,
                        "还没有输出最终文件",
                        "生成并返回最终文件")),
                List.of(new RuntimeV2CompletionEvidence(
                        "answer.present", "已有回答草稿", RuntimeV2CompletionEvidenceSource.ANSWER, true, "模型已产出回答草稿")),
                List.of(new RuntimeV2ObligationEntry(
                        "artifact.required",
                        "必须返回产物",
                        RuntimeV2CompletionBlockerSource.RUNTIME,
                        RuntimeV2ObligationStatus.OPEN,
                        "当前仍缺少最终产物",
                        "发布 artifact")));

        Map<String, Object> payload = assessment.toPayload();

        assertThat(payload.get("blockers")).isInstanceOf(List.class);
        assertThat(payload.get("evidences")).isInstanceOf(List.class);
        assertThat(payload.get("obligations")).isInstanceOf(List.class);
        assertThat(((List<?>) payload.get("blockers")).get(0)).isInstanceOf(Map.class);
        assertThat(((List<?>) payload.get("evidences")).get(0)).isInstanceOf(Map.class);
        assertThat(((List<?>) payload.get("obligations")).get(0)).isInstanceOf(Map.class);
    }
}
