package lingzhou.agent.backend.capability.agentruntime.v2.react;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionBlockerSource;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionEvidenceSource;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2EvidenceEntry;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2EvidenceStatus;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationEntry;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationStatus;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.junit.jupiter.api.Test;

class RuntimeV2ReactDecisionRegressionSupportTest {

    @Test
    void shouldBuildRegressionObservationWhenDecisionReturnsToCompletedTranslationStep() {
        String observation = RuntimeV2ReactDecisionRegressionSupport.buildRegressionObservation(
                datasetSqlOnlyState(),
                "parse_file",
                List.of("parse_file", "dataset.DS20260420103211J78Q.execute_dataset_sql"),
                4000);

        assertThat(observation).contains("status: DECISION_REGRESSION_RECONSIDER");
        assertThat(observation).contains("observationClass: decision-regression");
        assertThat(observation).contains("openRequirement: dataset.result.required");
        assertThat(observation).contains("action: reconsider");
    }

    @Test
    void shouldNotBuildRegressionObservationForDatasetSqlDecision() {
        String observation = RuntimeV2ReactDecisionRegressionSupport.buildRegressionObservation(
                datasetSqlOnlyState(),
                "dataset.DS20260420103211J78Q.execute_dataset_sql",
                List.of("parse_file", "dataset.DS20260420103211J78Q.execute_dataset_sql"),
                4000);

        assertThat(observation).isEmpty();
    }

    private RuntimeV2State datasetSqlOnlyState() {
        RuntimeV2State state = new RuntimeV2State(
                new ChatRuntimePreparedRequest(
                        ConversationSessionType.GENERAL_CHAT_V2,
                        null,
                        "session-1",
                        null,
                        null,
                        "先帮我将这个文档翻译成英文。然后查询一下市场部是谁报销金额最多",
                        "先帮我将这个文档翻译成英文。然后查询一下市场部是谁报销金额最多",
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
                        null,
                        false,
                        ""),
                1L,
                null,
                List.of(),
                null,
                null);
        state.replaceEvidenceLedger(List.of(
                new RuntimeV2EvidenceEntry(
                        "dataset.summary.known",
                        "数据集摘要",
                        RuntimeV2CompletionEvidenceSource.TOOL_OBSERVATION,
                        RuntimeV2EvidenceStatus.SATISFIED,
                        "已获取数据集摘要信息。",
                        "dataset.DS20260420103211J78Q.search_dataset_summary"),
                new RuntimeV2EvidenceEntry(
                        "dataset.schema.known",
                        "数据集结构",
                        RuntimeV2CompletionEvidenceSource.DATASET_SCHEMA,
                        RuntimeV2EvidenceStatus.SATISFIED,
                        "已确认数据集 schema。",
                        "dataset.DS20260420103211J78Q.get_dataset_schema"),
                new RuntimeV2EvidenceEntry(
                        "artifact.published",
                        "产物发布",
                        RuntimeV2CompletionEvidenceSource.ARTIFACT,
                        RuntimeV2EvidenceStatus.SATISFIED,
                        "已发布 artifact。",
                        "write_artifact")));
        state.replaceObligationLedger(List.of(
                new RuntimeV2ObligationEntry(
                        "dataset.summary.required",
                        "数据集摘要闭环",
                        RuntimeV2CompletionBlockerSource.SKILL,
                        RuntimeV2ObligationStatus.SATISFIED,
                        "该闭环已满足，无需重做。",
                        "先查看数据集 summary，再继续 schema 和 SQL。"),
                new RuntimeV2ObligationEntry(
                        "dataset.schema.required",
                        "结构确认闭环",
                        RuntimeV2CompletionBlockerSource.SKILL,
                        RuntimeV2ObligationStatus.SATISFIED,
                        "该闭环已满足，无需重做。",
                        "先查看数据集 summary/schema，再执行 SQL。"),
                new RuntimeV2ObligationEntry(
                        "dataset.result.required",
                        "数据结果闭环",
                        RuntimeV2CompletionBlockerSource.EVIDENCE,
                        RuntimeV2ObligationStatus.OPEN,
                        "当前技能命中数据集查询路径时，最终答复需要成功的数据查询结果支撑。",
                        "继续执行数据集查询，拿到有效结果后再结束。"),
                new RuntimeV2ObligationEntry(
                        "artifact.publish.required",
                        "交付物闭环",
                        RuntimeV2CompletionBlockerSource.USER,
                        RuntimeV2ObligationStatus.SATISFIED,
                        "该闭环已满足，无需重做。",
                        "继续生成并发布最终产物。")));
        return state;
    }
}
