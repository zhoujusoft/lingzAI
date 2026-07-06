package lingzhou.agent.backend.capability.agentruntime.v2.graph;

import lingzhou.agent.backend.business.chat.service.ChatSseEventBuilder;
import org.springframework.http.codec.ServerSentEvent;

public final class RuntimeV2GraphEventProjector {

    private RuntimeV2GraphEventProjector() {}

    public static ServerSentEvent<String> toServerSentEvent(RuntimeV2GraphEvent event) {
        if (event == null) {
            return null;
        }
        return switch (event.eventName()) {
            case "meta" -> ChatSseEventBuilder.meta(event.content());
            case "message" -> ChatSseEventBuilder.message(event.content() == null ? "" : String.valueOf(event.content()));
            case "content_delta" -> ChatSseEventBuilder.contentDelta(
                    event.content() == null ? "" : String.valueOf(event.content()));
            case "phase" -> ChatSseEventBuilder.phase(event.content());
            case "tool_call_started" -> ChatSseEventBuilder.toolCallStarted(event.content());
            case "tool_call_completed" -> ChatSseEventBuilder.toolCallCompleted(event.content());
            case "error" -> ChatSseEventBuilder.error(event.content() == null ? "" : String.valueOf(event.content()));
            case "done" -> ChatSseEventBuilder.done();
            default -> ChatSseEventBuilder.typed(event.eventName(), event.type(), event.content());
        };
    }
}
