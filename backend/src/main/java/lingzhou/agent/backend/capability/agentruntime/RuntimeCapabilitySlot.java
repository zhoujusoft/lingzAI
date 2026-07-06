package lingzhou.agent.backend.capability.agentruntime;

public enum RuntimeCapabilitySlot {
    TOOL_CALLING(10),
    RUNTIME_EXECUTION(20),
    EVENT_PERSISTENCE(70),
    TOKEN_USAGE(75),
    OBSERVABILITY(80),
    SAFETY_GUARD(85),
    LONG_TERM_MEMORY(90),
    QUALITY_GATE(100),
    SUB_AGENT(110),
    TASK_EXECUTION(120);

    private final int order;

    RuntimeCapabilitySlot(int order) {
        this.order = order;
    }

    public int order() {
        return order;
    }
}
