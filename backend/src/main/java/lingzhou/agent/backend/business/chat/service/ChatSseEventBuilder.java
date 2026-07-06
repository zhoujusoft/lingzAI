package lingzhou.agent.backend.business.chat.service;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.codec.ServerSentEvent;

public final class ChatSseEventBuilder {

    private ChatSseEventBuilder() {}

    public static ServerSentEvent<String> meta(Object content) {
        return typed("meta", "meta", content);
    }

    public static ServerSentEvent<String> message(String content) {
        return typed("message", "message", content);
    }

    public static ServerSentEvent<String> contentDelta(String delta) {
        return typed("content_delta", "message", delta);
    }

    public static ServerSentEvent<String> phase(Object content) {
        return typed("phase", "phase-progress", content);
    }

    public static ServerSentEvent<String> toolCallStarted(Object content) {
        return typed("tool_call_started", "tool", content);
    }

    public static ServerSentEvent<String> toolCallCompleted(Object content) {
        return typed("tool_call_completed", "result", content);
    }

    public static ServerSentEvent<String> error(String error) {
        return typed("error", "error", error);
    }

    public static ServerSentEvent<String> done() {
        return typed("done", "done", "[DONE]");
    }

    public static ServerSentEvent<String> typed(String eventName, String type, Object content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("content", content);
        return ServerSentEvent.builder(JSON.toJSONString(payload))
                .event(eventName)
                .build();
    }
}
