package lingzhou.agent.backend.skillstudio.runtime;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.agentruntime.usage.RuntimeRunUsageSnapshot;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioChangeProposal;
import lingzhou.agent.backend.skillstudio.protocol.SkillStudioValidationResult;

public record SkillStudioCreatorSkillDebugResult(
        String runtimeSkillName,
        String skillPath,
        List<String> toolNames,
        String requestPrompt,
        String rawOutput,
        SkillStudioChangeProposal parsedProposal,
        SkillStudioValidationResult validation,
        List<Map<String, Object>> toolEvents,
        List<String> toolLogs,
        List<String> executionLogs,
        RuntimeRunUsageSnapshot usageSnapshot,
        String parseError,
        boolean applied,
        String applyMessage) {}
