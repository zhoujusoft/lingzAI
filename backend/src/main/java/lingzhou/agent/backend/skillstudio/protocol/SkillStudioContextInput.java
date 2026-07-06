package lingzhou.agent.backend.skillstudio.protocol;

import java.util.List;

public record SkillStudioContextInput(
        String skillName,
        SkillStudioMode mode,
        String userGoal,
        DraftSummary draftSummary,
        MemorySummary memorySummary,
        CreatorHints hints,
        ToolResolution toolResolution,
        List<ToolProfile> toolProfiles,
        List<String> projectHints,
        List<String> projectConstraints) {

    public record DraftSummary(List<String> files, List<String> existingReferences, String skillSummary) {}

    public record MemorySummary(
            String baseTemplate,
            String capabilityTemplate,
            List<String> stableConstraints,
            List<String> referencePlan,
            List<String> notesForFutureEdits) {}

    public record CreatorHints(String preferredTemplate, boolean preferMinimalChange, boolean allowCreateReferences) {}

    public record ToolResolution(
            List<ResolvedTool> primaryTools,
            List<ResolvedTool> secondaryTools,
            List<MissingCapability> missingCapabilities) {}

    public record ResolvedTool(String toolName, double score, String matchMode, String reason) {}

    public record MissingCapability(String capability, String reason) {}

    public record ToolProfile(
            String toolName,
            String displayName,
            String toolType,
            String source,
            String capabilitySummary,
            List<String> requiredParams,
            List<String> schemaSummary,
            List<String> keyFields,
            List<String> usageHints) {}
}
