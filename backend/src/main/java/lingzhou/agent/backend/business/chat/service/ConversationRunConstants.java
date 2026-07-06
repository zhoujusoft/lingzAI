package lingzhou.agent.backend.business.chat.service;

public final class ConversationRunConstants {

    public static final String RUN_TYPE_CHAT = "CHAT";
    public static final String RUN_TYPE_TOOL = "TOOL";
    public static final String RUN_TYPE_CODE = "CODE";
    public static final String RUN_TYPE_SKILL = "SKILL";
    public static final String RUN_TYPE_DATASET = "DATASET";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_WAITING_INPUT = "WAITING_INPUT";
    public static final String STATUS_WAITING_APPROVAL = "WAITING_APPROVAL";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String PHASE_TRIAGE = "TRIAGE";
    public static final String PHASE_REASONING = "REASONING";
    public static final String PHASE_ACTION = "ACTION";
    public static final String PHASE_OBSERVATION = "OBSERVATION";
    public static final String PHASE_FINALIZING = "FINALIZING";

    private ConversationRunConstants() {}
}
