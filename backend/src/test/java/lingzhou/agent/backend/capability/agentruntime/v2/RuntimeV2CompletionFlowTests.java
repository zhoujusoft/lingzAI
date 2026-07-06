package lingzhou.agent.backend.capability.agentruntime.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.domain.enums.ConversationSessionType;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.business.chat.runtime.RequestScopedSkillRuntimeService;
import lingzhou.agent.backend.business.chat.runtime.RuntimeLoadedSkill;
import lingzhou.agent.backend.business.chat.runtime.RuntimeSkillDescriptor;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionAssessment;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionBlockerSource;
import lingzhou.agent.backend.capability.agentruntime.v2.completion.RuntimeV2CompletionGate;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2ContractCapability;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2EvidenceMatchMode;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2ExecutionRequirement;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContract;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2SkillContractResolver;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2TaskContractEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.contract.RuntimeV2TaskIntent;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2LedgerEngine;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Mode;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.junit.jupiter.api.Test;

class RuntimeV2CompletionFlowTests {

    private final RuntimeV2CompletionGate completionGate = new RuntimeV2CompletionGate();

    @Test
    void policyQaRequiresKnowledgeBaseEvidenceBeforeCompletion() {
        RuntimeLoadedSkill loadedSkill = new RuntimeLoadedSkill(23L, "expense-assistant", "报销助手", "报销技能");
        RuntimeV2State state = newState("出差到上海的报销标准是如何规定的？", List.of(loadedSkill));
        RuntimeV2LedgerEngine ledgerEngine = new RuntimeV2LedgerEngine(
                new FakeRequestScopedSkillRuntimeService(List.of(loadedSkill)),
                new RuntimeV2TaskContractEngine(new FakeSkillContractResolver(List.of(knowledgeBaseContract()))));

        ledgerEngine.refresh(state);
        RuntimeV2CompletionAssessment initial = completionGate.assess(state, "上海属于一线城市。");
        assertThat(initial.completionConfirmed()).isFalse();
        assertThat(initial.blockers()).extracting(blocker -> blocker.title()).contains("制度依据闭环");

        ledgerEngine.recordToolSuccess(state, "knowledge_base.KB00000053.search", "{\"success\":true}");
        RuntimeV2CompletionAssessment afterKnowledgeBase = completionGate.assess(state, "上海属于一线城市。");
        assertThat(afterKnowledgeBase.completionConfirmed()).isTrue();
    }

    @Test
    void datasetQueryStillBlockedWhenSqlRunsBeforeSummaryAndSchema() {
        RuntimeLoadedSkill loadedSkill = new RuntimeLoadedSkill(23L, "expense-assistant", "报销助手", "报销技能");
        RuntimeV2State state = newState("市场部报销总额是多少？", List.of(loadedSkill));
        RuntimeV2LedgerEngine ledgerEngine = new RuntimeV2LedgerEngine(
                new FakeRequestScopedSkillRuntimeService(List.of(loadedSkill)),
                new RuntimeV2TaskContractEngine(new FakeSkillContractResolver(List.of(datasetContract()))));

        ledgerEngine.refresh(state);
        RuntimeV2CompletionAssessment initial = completionGate.assess(state, "市场部报销总额为 6690 元。");
        assertThat(initial.completionConfirmed()).isFalse();
        assertThat(initial.blockers()).extracting(blocker -> blocker.title()).contains("数据集摘要闭环", "结构确认闭环", "数据结果闭环");

        ledgerEngine.recordToolSuccess(
                state,
                "dataset.DS20260420103211J78Q.execute_dataset_sql",
                "{\"success\":true,\"rows\":[{\"reimbursement_amount\":6690.0}]}");
        RuntimeV2CompletionAssessment afterSqlOnly = completionGate.assess(state, "市场部报销总额为 6690 元。");
        assertThat(afterSqlOnly.completionConfirmed()).isFalse();
        assertThat(afterSqlOnly.openObligationCount()).isEqualTo(2);
        assertThat(afterSqlOnly.blockers())
                .extracting(blocker -> blocker.title())
                .contains("数据集摘要闭环", "结构确认闭环");

        ledgerEngine.recordToolSuccess(
                state, "dataset.DS20260420103211J78Q.search_dataset_summary", "{\"success\":true}");
        RuntimeV2CompletionAssessment afterSummary = completionGate.assess(state, "市场部报销总额为 6690 元。");
        assertThat(afterSummary.completionConfirmed()).isFalse();
        assertThat(afterSummary.openObligationCount()).isEqualTo(1);
        assertThat(afterSummary.blockers())
                .extracting(blocker -> blocker.title())
                .containsExactly("结构确认闭环");

        ledgerEngine.recordToolSuccess(state, "dataset.DS20260420103211J78Q.get_dataset_schema", "{\"success\":true}");
        RuntimeV2CompletionAssessment afterSchema = completionGate.assess(state, "市场部报销总额为 6690 元。");
        assertThat(afterSchema.completionConfirmed()).isTrue();
    }

    @Test
    void artifactRequestRequiresPublishedArtifact() {
        RuntimeV2State state = newState("请生成一份销售分析报告并导出 html。", List.of());
        state.setMode(RuntimeV2Mode.CODE);
        RuntimeV2LedgerEngine ledgerEngine = new RuntimeV2LedgerEngine(
                new FakeRequestScopedSkillRuntimeService(List.of()),
                new RuntimeV2TaskContractEngine(new FakeSkillContractResolver(List.of())));

        ledgerEngine.refresh(state);
        RuntimeV2CompletionAssessment initial = completionGate.assess(state, "报告已经生成。");
        assertThat(initial.completionConfirmed()).isFalse();
        assertThat(initial.blockers()).extracting(blocker -> blocker.title()).contains("交付物闭环");

        ledgerEngine.recordToolSuccess(state, "file_write", "{\"success\":true}");
        RuntimeV2CompletionAssessment afterFileWrite = completionGate.assess(state, "报告已经生成。");
        assertThat(afterFileWrite.completionConfirmed()).isFalse();

        ledgerEngine.recordToolSuccess(state, "write_artifact", "{\"success\":true}");
        RuntimeV2CompletionAssessment afterArtifact = completionGate.assess(state, "报告已经生成。");
        assertThat(afterArtifact.completionConfirmed()).isTrue();
    }

    @Test
    void uncoveredIntentShouldBlockCompletionUntilMatchingSkillIsLoaded() {
        RuntimeLoadedSkill translationSkill = new RuntimeLoadedSkill(23L, "ai-doc-translation", "AI文档翻译", "翻译技能");
        RuntimeLoadedSkill expenseSkill = new RuntimeLoadedSkill(24L, "expense-assistant", "报销助手", "报销技能");

        RuntimeV2State translationOnlyState = newState(
                "先帮我将这个文档翻译成英文。然后查询一下市场部是谁报销金额最多", List.of(translationSkill), List.of(translationSkill, expenseSkill));
        RuntimeV2LedgerEngine ledgerEngine = new RuntimeV2LedgerEngine(
                new FakeRequestScopedSkillRuntimeService(List.of(translationSkill)),
                new RuntimeV2TaskContractEngine(new FakeSkillContractResolver(Map.of(
                        "ai-doc-translation", translationContract(),
                        "expense-assistant", datasetContract()))));

        ledgerEngine.refresh(translationOnlyState);
        RuntimeV2CompletionAssessment before = completionGate.assess(translationOnlyState, "文档已经翻译完成。");
        assertThat(before.completionConfirmed()).isFalse();
        assertThat(before.blockers()).extracting(blocker -> blocker.title()).contains("技能覆盖闭环");

        RuntimeV2State fullState = newState(
                "先帮我将这个文档翻译成英文。然后查询一下市场部是谁报销金额最多",
                List.of(translationSkill, expenseSkill),
                List.of(translationSkill, expenseSkill));
        RuntimeV2LedgerEngine fullLedgerEngine = new RuntimeV2LedgerEngine(
                new FakeRequestScopedSkillRuntimeService(List.of(translationSkill, expenseSkill)),
                new RuntimeV2TaskContractEngine(new FakeSkillContractResolver(Map.of(
                        "ai-doc-translation", translationContract(),
                        "expense-assistant", datasetContract()))));

        fullLedgerEngine.refresh(fullState);
        fullLedgerEngine.recordToolSuccess(
                fullState, "dataset.DS20260420103211J78Q.search_dataset_summary", "{\"success\":true}");
        fullLedgerEngine.recordToolSuccess(
                fullState, "dataset.DS20260420103211J78Q.get_dataset_schema", "{\"success\":true}");
        fullLedgerEngine.recordToolSuccess(
                fullState,
                "dataset.DS20260420103211J78Q.execute_dataset_sql",
                "{\"success\":true,\"rows\":[{\"employee_name\":\"张三\",\"total_reimbursed\":6690.0}]}");

        RuntimeV2CompletionAssessment after = completionGate.assess(fullState, "文档已经翻译完成，市场部报销金额最多的是张三。");
        assertThat(after.completionConfirmed()).isTrue();
    }

    private RuntimeV2State newState(String userMessage, List<RuntimeLoadedSkill> loadedSkills) {
        return newState(userMessage, loadedSkills, loadedSkills);
    }

    private RuntimeV2State newState(
            String userMessage, List<RuntimeLoadedSkill> loadedSkills, List<RuntimeLoadedSkill> availableSkills) {
        ChatRuntimePreparedRequest prepared = new ChatRuntimePreparedRequest(
                ConversationSessionType.GENERAL_CHAT_V2,
                null,
                "session-1",
                null,
                null,
                userMessage,
                userMessage,
                "normal",
                "general",
                "{}",
                null,
                List.of(),
                "",
                "",
                loadedSkills.isEmpty() ? "" : loadedSkills.get(0).runtimeSkillName(),
                availableSkills.stream()
                        .map(skill -> new RuntimeSkillDescriptor(
                                skill.skillId(), skill.runtimeSkillName(), skill.displayName(), skill.description()))
                        .toList(),
                loadedSkills,
                null,
                false,
                "");
        return new RuntimeV2State(prepared, 1L, null, List.of(), null, null);
    }

    private RuntimeV2SkillContract knowledgeBaseContract() {
        return new RuntimeV2SkillContract(
                "expense-assistant",
                "报销助手",
                List.of("knowledge_base.KB00000053.search"),
                List.of(RuntimeV2ContractCapability.KNOWLEDGE_BASE),
                List.of(new RuntimeV2ExecutionRequirement(
                        "knowledge.base.required",
                        "制度依据闭环",
                        RuntimeV2CompletionBlockerSource.EVIDENCE,
                        "当前技能命中知识库检索能力时，最终答复需要有知识库依据支撑。",
                        "先查询知识库，再给出最终答复。",
                        List.of("knowledge.base.hit"),
                        RuntimeV2EvidenceMatchMode.ANY_OF,
                        List.of("knowledge_base.KB00000053.search"),
                        List.of(RuntimeV2TaskIntent.POLICY_QA))));
    }

    private RuntimeV2SkillContract datasetContract() {
        List<String> datasetTools = List.of(
                "dataset.DS20260420103211J78Q.search_dataset_summary",
                "dataset.DS20260420103211J78Q.get_dataset_schema",
                "dataset.DS20260420103211J78Q.execute_dataset_sql");
        return new RuntimeV2SkillContract(
                "expense-assistant",
                "报销助手",
                datasetTools,
                List.of(RuntimeV2ContractCapability.DATASET_QUERY),
                List.of(
                        new RuntimeV2ExecutionRequirement(
                                "dataset.summary.required",
                                "数据集摘要闭环",
                                RuntimeV2CompletionBlockerSource.SKILL,
                                "当前技能包含数据集查询路径，写 SQL 前应先查看数据集摘要，确认候选表和对象编码。",
                                "先查看数据集 summary，再继续 schema 和 SQL。",
                                List.of("dataset.summary.known"),
                                RuntimeV2EvidenceMatchMode.ALL_OF,
                                datasetTools,
                                List.of(RuntimeV2TaskIntent.DATA_QUERY)),
                        new RuntimeV2ExecutionRequirement(
                                "dataset.schema.required",
                                "结构确认闭环",
                                RuntimeV2CompletionBlockerSource.SKILL,
                                "当前技能包含数据集查询路径，执行 SQL 前应先确认数据集 schema 和字段。",
                                "先查看数据集 summary/schema，再执行 SQL。",
                                List.of("dataset.schema.known"),
                                RuntimeV2EvidenceMatchMode.ALL_OF,
                                datasetTools,
                                List.of(RuntimeV2TaskIntent.DATA_QUERY)),
                        new RuntimeV2ExecutionRequirement(
                                "dataset.result.required",
                                "数据结果闭环",
                                RuntimeV2CompletionBlockerSource.EVIDENCE,
                                "当前技能命中数据集查询路径时，最终答复需要成功的数据查询结果支撑。",
                                "继续执行数据集查询，拿到有效结果后再结束。",
                                List.of("dataset.query.success"),
                                RuntimeV2EvidenceMatchMode.ALL_OF,
                                datasetTools,
                                List.of(RuntimeV2TaskIntent.DATA_QUERY))));
    }

    private static final class FakeRequestScopedSkillRuntimeService extends RequestScopedSkillRuntimeService {

        private final List<RuntimeLoadedSkill> loadedSkills;

        private FakeRequestScopedSkillRuntimeService(List<RuntimeLoadedSkill> loadedSkills) {
            super(null);
            this.loadedSkills = loadedSkills == null ? List.of() : List.copyOf(loadedSkills);
        }

        @Override
        public List<RuntimeLoadedSkill> extractLoadedSkills(
                lingzhou.agent.spring.ai.skill.core.SkillKit skillKit, List<RuntimeSkillDescriptor> availableSkills) {
            return loadedSkills;
        }
    }

    private static final class FakeSkillContractResolver extends RuntimeV2SkillContractResolver {

        private final List<RuntimeV2SkillContract> contracts;
        private final Map<String, RuntimeV2SkillContract> contractMap;

        private FakeSkillContractResolver(List<RuntimeV2SkillContract> contracts) {
            this(
                    contracts == null
                            ? Map.of()
                            : contracts.stream()
                                    .collect(java.util.stream.Collectors.toMap(
                                            contract -> contract.skillName(),
                                            contract -> contract,
                                            (left, right) -> left,
                                            java.util.LinkedHashMap::new)));
        }

        private FakeSkillContractResolver(Map<String, RuntimeV2SkillContract> contractMap) {
            super(null, null, null, null);
            this.contractMap = contractMap == null ? Map.of() : Map.copyOf(contractMap);
            this.contracts = List.copyOf(this.contractMap.values());
        }

        @Override
        public List<RuntimeV2SkillContract> resolveActiveContracts(RuntimeV2State state) {
            if (state == null || state.prepared() == null || state.prepared().loadedSkills() == null) {
                return contracts;
            }
            List<RuntimeV2SkillContract> active = new java.util.ArrayList<>();
            for (RuntimeLoadedSkill loadedSkill : state.prepared().loadedSkills()) {
                if (loadedSkill == null || loadedSkill.runtimeSkillName() == null) {
                    continue;
                }
                RuntimeV2SkillContract contract = contractMap.get(loadedSkill.runtimeSkillName());
                if (contract != null) {
                    active.add(contract);
                }
            }
            return List.copyOf(active);
        }
    }

    private RuntimeV2SkillContract translationContract() {
        return new RuntimeV2SkillContract(
                "ai-doc-translation",
                "AI文档翻译",
                List.of("file_write", "run_python", "write_artifact"),
                List.of(RuntimeV2ContractCapability.ARTIFACT_DELIVERY),
                List.of());
    }
}
