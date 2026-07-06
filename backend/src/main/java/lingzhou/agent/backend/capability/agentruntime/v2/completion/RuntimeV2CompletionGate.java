package lingzhou.agent.backend.capability.agentruntime.v2.completion;

import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2EvidenceEntry;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2EvidenceStatus;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationEntry;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationStatus;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2CompletionGate {

    public RuntimeV2CompletionAssessment assess(RuntimeV2State state, String draftAnswer) {
        List<RuntimeV2CompletionBlocker> blockers = new ArrayList<>();
        List<RuntimeV2CompletionEvidence> evidences = new ArrayList<>();
        List<RuntimeV2ObligationEntry> obligations =
                state == null ? List.of() : new ArrayList<>(state.obligationLedger());

        boolean finalCandidate = StringUtils.hasText(draftAnswer);
        evidences.add(new RuntimeV2CompletionEvidence(
                "final.answer.present",
                "最终答复草稿",
                RuntimeV2CompletionEvidenceSource.ANSWER,
                finalCandidate,
                finalCandidate ? "模型已给出 final answer。" : "当前没有有效的 final answer。"));
        if (!finalCandidate) {
            blockers.add(new RuntimeV2CompletionBlocker(
                    "FINAL_ANSWER_EMPTY",
                    "缺少最终答复",
                    RuntimeV2CompletionBlockerSource.RUNTIME,
                    "模型尚未产出可交付的 final answer。",
                    "继续推理并生成最终答复。"));
        }

        List<RuntimeV2EvidenceEntry> evidenceEntries = state == null ? List.of() : state.evidenceLedger();
        for (RuntimeV2EvidenceEntry entry : evidenceEntries) {
            if (entry == null || !StringUtils.hasText(entry.code())) {
                continue;
            }
            evidences.add(new RuntimeV2CompletionEvidence(
                    entry.code(),
                    entry.title(),
                    entry.source(),
                    entry.status() == RuntimeV2EvidenceStatus.SATISFIED,
                    entry.detail()));
        }

        for (RuntimeV2ObligationEntry obligation : obligations) {
            if (obligation == null || obligation.status() == null) {
                continue;
            }
            if (obligation.status() == RuntimeV2ObligationStatus.OPEN
                    || obligation.status() == RuntimeV2ObligationStatus.IN_PROGRESS
                    || obligation.status() == RuntimeV2ObligationStatus.FAILED) {
                blockers.add(new RuntimeV2CompletionBlocker(
                        obligation.code().toUpperCase(),
                        obligation.title(),
                        obligation.source(),
                        obligation.detail(),
                        obligation.expectedAction()));
            }
        }

        return new RuntimeV2CompletionAssessment(finalCandidate, blockers.isEmpty(), blockers, evidences, obligations);
    }
}
