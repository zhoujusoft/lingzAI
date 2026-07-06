package lingzhou.agent.backend.capability.agentruntime.v2.observation;

import java.util.List;
import org.springframework.util.StringUtils;

public final class RuntimeV2GuardObservationFactory {

    private RuntimeV2GuardObservationFactory() {}

    public static String buildDuplicateReadOnlyObservation(String toolName, String signature, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "status", "DUPLICATE_READONLY_TOOL_SKIPPED");
        appendObservationLine(builder, "observationClass", "guard-intercept");
        appendObservationLine(builder, "toolName", toolName);
        appendObservationLine(builder, "signature", signature);
        appendObservationLine(builder, "readOnly", "true");
        appendObservationLine(builder, "duplicateReadOnly", "true");
        appendObservationLine(builder, "reusePriorObservation", "true");
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    public static String buildFixedSkillScriptRewriteBlockedObservation(
            String attemptedPath, List<String> fixedScriptPaths, int maxPromptLength) {
        StringBuilder builder = new StringBuilder();
        appendObservationLine(builder, "status", "SKILL_FIXED_SCRIPT_REWRITE_BLOCKED");
        appendObservationLine(builder, "observationClass", "guard-intercept");
        appendObservationLine(builder, "toolName", "file_write");
        appendObservationLine(builder, "targetKind", "python-script");
        appendObservationLine(builder, "attemptedPath", attemptedPath);
        appendObservationLine(builder, "fixedSkillScripts", joinPaths(fixedScriptPaths));
        appendObservationLine(builder, "failureKind", "fixed-skill-script-rewrite-blocked");
        appendObservationLine(builder, "skillScriptPinned", "true");
        appendObservationLine(builder, "rewriteBlocked", "true");
        return trimForPrompt(builder.toString(), maxPromptLength);
    }

    private static String joinPaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        return String.join(", ", paths);
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
}
