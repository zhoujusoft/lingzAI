package lingzhou.agent.backend.capability.agentruntime.v2.prompt;

public final class RuntimeV2PromptCatalog {

    public static final String REACT_DIRECT_SYSTEM = "react/direct-system";
    public static final String REACT_REASONING_SYSTEM = "react/reasoning-system";
    public static final String REACT_REASONING_USER = "react/reasoning-user";
    public static final String REACT_REASONING_REPAIR_USER = "react/reasoning-repair-user";
    public static final String REACT_FINAL_SYSTEM = "react/final-system";
    public static final String REACT_FINAL_USER = "react/final-user";

    public static final String CODE_PLAN_SYSTEM = "code/plan-system";
    public static final String CODE_PLAN_USER = "code/plan-user";
    public static final String CODE_SCRIPT_SYSTEM = "code/script-system";
    public static final String CODE_SCRIPT_USER = "code/script-user";

    public static final String SHARED_ACTIVE_SKILL_SECTION = "shared/active-skill-section";
    public static final String SHARED_ACTIVE_SKILL_ITEM = "shared/active-skill-item";
    public static final String SHARED_TOOL_SUMMARY_SECTION = "shared/tool-summary-section";
    public static final String SHARED_TOOL_SUMMARY_ITEM = "shared/tool-summary-item";
    public static final String SHARED_TOOL_SUMMARY_EMPTY = "shared/tool-summary-empty";
    public static final String SHARED_OBSERVATION_TRACE_SECTION = "shared/observation-trace-section";
    public static final String SHARED_OBSERVATION_TRACE_ITEM = "shared/observation-trace-item";
    public static final String SHARED_CODE_CAPABILITY_SECTION = "shared/code-capability-section";
    public static final String SHARED_COMPLETION_BLOCKERS_SECTION = "shared/completion-blockers-section";
    public static final String SHARED_COMPLETION_BLOCKER_ITEM = "shared/completion-blocker-item";
    public static final String SHARED_TASK_CONTRACT_SECTION = "shared/task-contract-section";
    public static final String SHARED_TASK_CONTRACT_INTENTS_SECTION = "shared/task-contract-intents-section";
    public static final String SHARED_TASK_CONTRACT_SKILLS_SECTION = "shared/task-contract-skills-section";
    public static final String SHARED_TASK_CONTRACT_SKILL_ITEM = "shared/task-contract-skill-item";
    public static final String SHARED_TASK_CONTRACT_REQUIREMENTS_SECTION = "shared/task-contract-requirements-section";
    public static final String SHARED_TASK_CONTRACT_REQUIREMENT_ITEM = "shared/task-contract-requirement-item";
    public static final String SHARED_PROGRESS_STATUS_SECTION = "shared/progress-status-section";
    public static final String SHARED_PROGRESS_STATUS_OBLIGATION_ITEM = "shared/progress-status-obligation-item";
    public static final String SHARED_PROGRESS_STATUS_EVIDENCE_ITEM = "shared/progress-status-evidence-item";

    private RuntimeV2PromptCatalog() {}
}
