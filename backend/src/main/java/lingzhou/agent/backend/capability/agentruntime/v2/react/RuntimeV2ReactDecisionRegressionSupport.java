package lingzhou.agent.backend.capability.agentruntime.v2.react;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2EvidenceEntry;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2EvidenceStatus;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationEntry;
import lingzhou.agent.backend.capability.agentruntime.v2.ledger.RuntimeV2ObligationStatus;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2State;
import org.springframework.util.StringUtils;

public final class RuntimeV2ReactDecisionRegressionSupport {

    private static final String DATASET_RESULT_REQUIRED = "dataset.result.required";
    private static final String DATASET_SUMMARY_REQUIRED = "dataset.summary.required";
    private static final String DATASET_SCHEMA_REQUIRED = "dataset.schema.required";
    private static final String DATASET_SUMMARY_KNOWN = "dataset.summary.known";
    private static final String DATASET_SCHEMA_KNOWN = "dataset.schema.known";
    private static final String DATASET_QUERY_SUCCESS = "dataset.query.success";

    private RuntimeV2ReactDecisionRegressionSupport() {}

    public static String buildRegressionObservation(
            RuntimeV2State state, String toolName, Collection<String> availableToolNames, int maxPromptLength) {
        if (!mustContinueDatasetQuery(state, availableToolNames) || isDatasetSqlTool(toolName)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "status", "DECISION_REGRESSION_RECONSIDER");
        appendObservationLine(builder, "observationClass", "decision-regression");
        appendObservationLine(builder, "toolName", normalize(toolName));
        appendObservationLine(builder, "openRequirement", DATASET_RESULT_REQUIRED);
        appendObservationLine(
                builder, "completedRequirements", DATASET_SUMMARY_REQUIRED + ", " + DATASET_SCHEMA_REQUIRED);
        appendObservationLine(builder, "regressionDetected", "true");
        appendObservationLine(builder, "action", "reconsider");
        appendObservationLine(builder, "hint", "当前回到了已完成子任务，请结合最新进度重新判断下一步");
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    private static boolean mustContinueDatasetQuery(RuntimeV2State state, Collection<String> availableToolNames) {
        if (state == null || !hasDatasetSqlTool(availableToolNames)) {
            return false;
        }
        List<RuntimeV2ObligationEntry> pendingObligations = state.obligationLedger().stream()
                .filter(RuntimeV2ReactDecisionRegressionSupport::isPendingObligation)
                .toList();
        if (pendingObligations.size() != 1
                || !matchesObligationCode(pendingObligations.get(0), DATASET_RESULT_REQUIRED)) {
            return false;
        }
        if (!hasSatisfiedEvidence(state, DATASET_SUMMARY_KNOWN)
                || !hasSatisfiedEvidence(state, DATASET_SCHEMA_KNOWN)
                || hasSatisfiedEvidence(state, DATASET_QUERY_SUCCESS)) {
            return false;
        }
        return hasSatisfiedObligation(state, DATASET_SUMMARY_REQUIRED)
                && hasSatisfiedObligation(state, DATASET_SCHEMA_REQUIRED);
    }

    private static boolean hasDatasetSqlTool(Collection<String> availableToolNames) {
        return availableToolNames != null
                && availableToolNames.stream().anyMatch(RuntimeV2ReactDecisionRegressionSupport::isDatasetSqlTool);
    }

    private static boolean isDatasetSqlTool(String toolName) {
        String normalized = normalize(toolName);
        return "execute_dataset_sql".equals(normalized) || normalized.contains(".execute_dataset_sql");
    }

    private static boolean hasSatisfiedObligation(RuntimeV2State state, String code) {
        if (state == null || !StringUtils.hasText(code)) {
            return false;
        }
        return state.obligationLedger().stream()
                .anyMatch(entry ->
                        matchesObligationCode(entry, code) && entry.status() == RuntimeV2ObligationStatus.SATISFIED);
    }

    private static boolean hasSatisfiedEvidence(RuntimeV2State state, String code) {
        if (state == null || !StringUtils.hasText(code)) {
            return false;
        }
        return state.evidenceLedger().stream()
                .anyMatch(entry ->
                        matchesEvidenceCode(entry, code) && entry.status() == RuntimeV2EvidenceStatus.SATISFIED);
    }

    private static boolean isPendingObligation(RuntimeV2ObligationEntry entry) {
        return entry != null
                && entry.status() != null
                && entry.status() != RuntimeV2ObligationStatus.SATISFIED
                && entry.status() != RuntimeV2ObligationStatus.WAIVED;
    }

    private static boolean matchesObligationCode(RuntimeV2ObligationEntry entry, String code) {
        return entry != null && StringUtils.hasText(code) && code.equals(entry.code());
    }

    private static boolean matchesEvidenceCode(RuntimeV2EvidenceEntry entry, String code) {
        return entry != null && StringUtils.hasText(code) && code.equals(entry.code());
    }

    private static void appendObservationLine(StringBuilder builder, String key, String value) {
        if (builder == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(key.trim()).append(": ").append(value.trim());
    }

    private static String trimForPrompt(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        if (maxLength <= 0 || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "\n...[truncated]";
    }

    private static String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }
}
