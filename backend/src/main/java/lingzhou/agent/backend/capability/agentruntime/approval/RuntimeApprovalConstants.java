package lingzhou.agent.backend.capability.agentruntime.approval;

public final class RuntimeApprovalConstants {

    public static final String APPROVAL_PENDING = "PENDING";
    public static final String APPROVAL_APPROVED = "APPROVED";
    public static final String APPROVAL_REJECTED = "REJECTED";
    public static final String APPROVAL_CANCELLED = "CANCELLED";

    public static final String EXECUTION_NOT_STARTED = "NOT_STARTED";
    public static final String EXECUTION_RUNNING = "RUNNING";
    public static final String EXECUTION_SUCCEEDED = "SUCCEEDED";
    public static final String EXECUTION_FAILED = "FAILED";
    public static final String EXECUTION_SKIPPED = "SKIPPED";

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    private RuntimeApprovalConstants() {}
}
