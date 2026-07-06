package lingzhou.agent.backend.capability.agentruntime.personal;

import java.util.List;

public record PersonalAgentExecutionPrecheck(
        String status,
        String primaryExecutor,
        String selectedSkill,
        String stepExecutor,
        String relevantSkill,
        boolean allowSkillInternals,
        // 兼容旧快照字段，新逻辑请只读取 allowCodeExecution。
        boolean codeEscalationCandidate,
        boolean allowCodeExecution,
        boolean hasAttachments,
        boolean needsUserConfirmation,
        List<String> blockers,
        List<String> warnings,
        List<String> notes) {}
