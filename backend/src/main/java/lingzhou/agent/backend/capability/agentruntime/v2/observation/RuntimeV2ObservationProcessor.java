package lingzhou.agent.backend.capability.agentruntime.v2.observation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2ObservationProcessor {

    private static final int DUPLICATE_THRESHOLD = 3;
    private static final int LARGE_OBSERVATION_THRESHOLD = 4000;
    private static final int MAX_TOTAL_OBSERVATION_CHARS = 12000;
    private static final int MIN_MESSAGES_FOR_SUMMARIZE = 24;

    public Map<String, Object> evaluate(List<Map<String, Object>> observationTrace) {
        return evaluate(observationTrace, 0);
    }

    public Map<String, Object> evaluate(List<Map<String, Object>> observationTrace, int messageCount) {
        List<Map<String, Object>> safeTrace = observationTrace == null ? List.of() : observationTrace;
        String latestObservation = resolveLatestObservation(safeTrace);
        int totalChars = safeTrace.stream()
                .map(item -> normalizeText(item.get("observation")))
                .mapToInt(String::length)
                .sum();
        int observationCount = safeTrace.size();
        int safeMessageCount = Math.max(0, messageCount);
        boolean duplicateObservation = detectDuplicateObservations(safeTrace, latestObservation, DUPLICATE_THRESHOLD);
        boolean largeObservation = latestObservation.length() > LARGE_OBSERVATION_THRESHOLD;
        boolean totalOverflow = totalChars > MAX_TOTAL_OBSERVATION_CHARS;
        boolean messagePressure = safeMessageCount >= MIN_MESSAGES_FOR_SUMMARIZE;
        boolean shouldSummarize = largeObservation || totalOverflow || messagePressure;

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("observationCount", observationCount);
        state.put("messageCount", safeMessageCount);
        state.put("latestObservationLength", latestObservation.length());
        state.put("totalChars", totalChars);
        state.put("duplicateObservation", duplicateObservation);
        state.put("messagePressure", messagePressure);
        state.put("shouldSummarize", shouldSummarize);
        state.put("summaryReason", resolveSummaryReason(largeObservation, totalOverflow, messagePressure));
        state.put("loopTerminated", duplicateObservation);
        if (duplicateObservation) {
            state.put("error", "连续 3 次 observation 相同，已强制终止图循环");
        }
        return Map.copyOf(state);
    }

    private String resolveLatestObservation(List<Map<String, Object>> observationTrace) {
        if (observationTrace == null || observationTrace.isEmpty()) {
            return "";
        }
        Map<String, Object> last = observationTrace.get(observationTrace.size() - 1);
        return normalizeText(last.get("observation"));
    }

    private boolean detectDuplicateObservations(
            List<Map<String, Object>> observationTrace, String latestObservation, int threshold) {
        if (observationTrace == null
                || observationTrace.size() < threshold
                || !StringUtils.hasText(latestObservation)
                || threshold <= 1) {
            return false;
        }
        int start = observationTrace.size() - threshold;
        for (int index = start; index < observationTrace.size(); index += 1) {
            String candidate = normalizeText(observationTrace.get(index).get("observation"));
            if (!latestObservation.equals(candidate)) {
                return false;
            }
        }
        return true;
    }

    private String resolveSummaryReason(boolean largeObservation, boolean totalOverflow, boolean messagePressure) {
        if (largeObservation) {
            return "large-observation";
        }
        if (totalOverflow) {
            return "total-overflow";
        }
        if (messagePressure) {
            return "message-pressure";
        }
        return "";
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
