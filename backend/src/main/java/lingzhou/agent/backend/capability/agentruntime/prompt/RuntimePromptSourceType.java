package lingzhou.agent.backend.capability.agentruntime.prompt;

public enum RuntimePromptSourceType {
    MODEL(100),
    CONFIG(200),
    SCENE(300),
    PROTOCOL(400),
    CAPABILITY(500),
    REQUEST(900);

    private final int defaultOrder;

    RuntimePromptSourceType(int defaultOrder) {
        this.defaultOrder = defaultOrder;
    }

    public int defaultOrder() {
        return defaultOrder;
    }
}
