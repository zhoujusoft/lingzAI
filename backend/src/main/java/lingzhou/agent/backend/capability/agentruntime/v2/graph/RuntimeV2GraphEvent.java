package lingzhou.agent.backend.capability.agentruntime.v2.graph;

public record RuntimeV2GraphEvent(String eventName, String type, Object content) {

    public static RuntimeV2GraphEvent meta(Object content) {
        return new RuntimeV2GraphEvent("meta", "meta", content);
    }

    public static RuntimeV2GraphEvent message(String content) {
        return new RuntimeV2GraphEvent("message", "message", content);
    }

    public static RuntimeV2GraphEvent contentDelta(String delta) {
        return new RuntimeV2GraphEvent("content_delta", "message", delta);
    }

    public static RuntimeV2GraphEvent phase(Object content) {
        return new RuntimeV2GraphEvent("phase", "phase-progress", content);
    }

    public static RuntimeV2GraphEvent toolCallStarted(Object content) {
        return new RuntimeV2GraphEvent("tool_call_started", "tool", content);
    }

    public static RuntimeV2GraphEvent toolCallCompleted(Object content) {
        return new RuntimeV2GraphEvent("tool_call_completed", "result", content);
    }

    public static RuntimeV2GraphEvent approvalRequired(Object content) {
        return new RuntimeV2GraphEvent("approval_required", "approval", content);
    }

    public static RuntimeV2GraphEvent error(String error) {
        return new RuntimeV2GraphEvent("error", "error", error);
    }

    public static RuntimeV2GraphEvent done() {
        return new RuntimeV2GraphEvent("done", "done", "[DONE]");
    }
}
