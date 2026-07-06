package lingzhou.agent.backend.capability.agentruntime.usage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

public final class RuntimeRunUsageSnapshotMerger {

    private RuntimeRunUsageSnapshotMerger() {}

    public static RuntimeRunUsageSnapshot merge(List<RuntimeRunUsageSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        List<RuntimeRunUsageSnapshot> normalized =
                snapshots.stream().filter(Objects::nonNull).toList();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() == 1) {
            return normalized.get(0);
        }
        List<RuntimeModelCallUsage> mergedCalls = new ArrayList<>();
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;
        int toolCallCount = 0;
        boolean usageAvailable = true;
        long startedAtMillis = Long.MAX_VALUE;
        long completedAtMillis = Long.MIN_VALUE;
        Long modelId = null;
        String modelProvider = null;
        String modelName = null;
        String adapterType = null;
        String runStatus = "COMPLETED";
        int nextCallNo = 1;
        for (RuntimeRunUsageSnapshot snapshot : normalized) {
            usageAvailable = usageAvailable && snapshot.usageAvailable();
            promptTokens = sumNullable(promptTokens, snapshot.promptTokens());
            completionTokens = sumNullable(completionTokens, snapshot.completionTokens());
            totalTokens = sumNullable(totalTokens, snapshot.totalTokens());
            toolCallCount += snapshot.toolCallCount();
            startedAtMillis = Math.min(startedAtMillis, snapshot.startedAtMillis());
            completedAtMillis = Math.max(completedAtMillis, snapshot.completedAtMillis());
            modelId = mergeModelId(modelId, snapshot.modelId());
            modelProvider = mergeText(modelProvider, snapshot.modelProvider());
            modelName = mergeText(modelName, snapshot.modelName());
            adapterType = mergeText(adapterType, snapshot.adapterType());
            runStatus = mergeRunStatus(runStatus, snapshot.runStatus());
            for (RuntimeModelCallUsage call : snapshot.modelCalls()) {
                mergedCalls.add(new RuntimeModelCallUsage(
                        nextCallNo++,
                        call.status(),
                        call.usageAvailable(),
                        call.promptTokens(),
                        call.completionTokens(),
                        call.totalTokens(),
                        call.outputChars(),
                        call.startedAtMillis(),
                        call.completedAtMillis(),
                        call.durationMs()));
            }
        }
        if (startedAtMillis == Long.MAX_VALUE) {
            startedAtMillis = System.currentTimeMillis();
        }
        if (completedAtMillis == Long.MIN_VALUE) {
            completedAtMillis = startedAtMillis;
        }
        if (mergedCalls.isEmpty()) {
            usageAvailable = false;
        }
        return new RuntimeRunUsageSnapshot(
                runStatus,
                usageAvailable,
                promptTokens,
                completionTokens,
                totalTokens,
                mergedCalls.size(),
                toolCallCount,
                Math.max(0L, completedAtMillis - startedAtMillis),
                startedAtMillis,
                completedAtMillis,
                modelId,
                modelProvider,
                modelName,
                adapterType,
                List.copyOf(mergedCalls));
    }

    private static Integer sumNullable(Integer left, Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return safeInt(left) + safeInt(right);
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static Long mergeModelId(Long current, Long incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null || Objects.equals(current, incoming)) {
            return current;
        }
        return null;
    }

    private static String mergeText(String current, String incoming) {
        if (!StringUtils.hasText(current)) {
            return normalize(incoming);
        }
        String normalizedIncoming = normalize(incoming);
        if (!StringUtils.hasText(normalizedIncoming) || current.equals(normalizedIncoming)) {
            return current;
        }
        return "multiple";
    }

    private static String mergeRunStatus(String current, String incoming) {
        String normalizedIncoming = normalize(incoming).toUpperCase();
        if (!StringUtils.hasText(normalizedIncoming)) {
            return current;
        }
        if ("FAILED".equals(normalizedIncoming)) {
            return "FAILED";
        }
        if ("CANCELLED".equals(normalizedIncoming) && !"FAILED".equals(current)) {
            return "CANCELLED";
        }
        if ("RUNNING".equals(normalizedIncoming) && !("FAILED".equals(current) || "CANCELLED".equals(current))) {
            return "RUNNING";
        }
        return current;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
