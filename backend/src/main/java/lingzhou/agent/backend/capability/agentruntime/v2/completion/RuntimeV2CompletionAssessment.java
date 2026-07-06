package lingzhou.agent.backend.capability.agentruntime.v2.completion;

import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationEntry;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationStatus;

public record RuntimeV2CompletionAssessment(
        boolean finalCandidate,
        boolean completionConfirmed,
        List<RuntimeV2CompletionBlocker> blockers,
        List<RuntimeV2CompletionEvidence> evidences,
        List<RuntimeV2ObligationEntry> obligations) {

    public RuntimeV2CompletionAssessment {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        evidences = evidences == null ? List.of() : List.copyOf(evidences);
        obligations = obligations == null ? List.of() : List.copyOf(obligations);
    }

    public boolean blocked() {
        return !blockers.isEmpty();
    }

    public String firstBlockerSummary() {
        if (blockers.isEmpty()) {
            return "";
        }
        RuntimeV2CompletionBlocker blocker = blockers.get(0);
        String title = blocker.title() == null ? "" : blocker.title().trim();
        String detail = blocker.detail() == null ? "" : blocker.detail().trim();
        if (!title.isEmpty() && !detail.isEmpty()) {
            return title + "：" + detail;
        }
        return !title.isEmpty() ? title : detail;
    }

    public int blockerCount() {
        return blockers.size();
    }

    public int totalEvidenceCount() {
        return evidences.size();
    }

    public int satisfiedEvidenceCount() {
        int count = 0;
        for (RuntimeV2CompletionEvidence evidence : evidences) {
            if (evidence != null && evidence.satisfied()) {
                count += 1;
            }
        }
        return count;
    }

    public int openObligationCount() {
        int count = 0;
        for (RuntimeV2ObligationEntry obligation : obligations) {
            if (obligation != null
                    && (obligation.status() == RuntimeV2ObligationStatus.OPEN
                            || obligation.status() == RuntimeV2ObligationStatus.IN_PROGRESS
                            || obligation.status() == RuntimeV2ObligationStatus.FAILED)) {
                count += 1;
            }
        }
        return count;
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("finalCandidate", finalCandidate);
        payload.put("completionConfirmed", completionConfirmed);
        payload.put("blockers", blockerPayloads());
        payload.put("evidences", evidencePayloads());
        payload.put("obligations", obligationPayloads());
        payload.put("blockerCount", blockerCount());
        payload.put("satisfiedEvidenceCount", satisfiedEvidenceCount());
        payload.put("totalEvidenceCount", totalEvidenceCount());
        payload.put("openObligationCount", openObligationCount());
        return payload;
    }

    private List<Map<String, Object>> blockerPayloads() {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (RuntimeV2CompletionBlocker blocker : blockers) {
            if (blocker == null) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", blocker.code());
            payload.put("title", blocker.title());
            payload.put(
                    "source", blocker.source() == null ? "" : blocker.source().name());
            payload.put("detail", blocker.detail());
            payload.put("expectedAction", blocker.expectedAction());
            payloads.add(Map.copyOf(payload));
        }
        return List.copyOf(payloads);
    }

    private List<Map<String, Object>> evidencePayloads() {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (RuntimeV2CompletionEvidence evidence : evidences) {
            if (evidence == null) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", evidence.code());
            payload.put("title", evidence.title());
            payload.put(
                    "source", evidence.source() == null ? "" : evidence.source().name());
            payload.put("satisfied", evidence.satisfied());
            payload.put("detail", evidence.detail());
            payloads.add(Map.copyOf(payload));
        }
        return List.copyOf(payloads);
    }

    private List<Map<String, Object>> obligationPayloads() {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (RuntimeV2ObligationEntry obligation : obligations) {
            if (obligation == null) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", obligation.code());
            payload.put("title", obligation.title());
            payload.put(
                    "source",
                    obligation.source() == null ? "" : obligation.source().name());
            payload.put(
                    "status",
                    obligation.status() == null ? "" : obligation.status().name());
            payload.put("detail", obligation.detail());
            payload.put("expectedAction", obligation.expectedAction());
            payloads.add(Map.copyOf(payload));
        }
        return List.copyOf(payloads);
    }

    public String toJson() {
        return JSON.toJSONString(toPayload());
    }
}
