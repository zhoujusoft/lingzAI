package lingzhou.agent.backend.business.chat.execution.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record RuntimeExecutionResult(
        boolean success,
        RuntimeExecutionAction action,
        String textOutput,
        Map<String, Object> data,
        String errorCode,
        String errorMessage) {

    public static RuntimeExecutionResult success(
            RuntimeExecutionAction action, String textOutput, Map<String, Object> data) {
        return new RuntimeExecutionResult(
                true, action, textOutput, data == null ? Map.of() : new LinkedHashMap<>(data), null, null);
    }

    public static RuntimeExecutionResult failure(RuntimeExecutionAction action, String errorCode, String errorMessage) {
        return new RuntimeExecutionResult(false, action, null, Map.of(), errorCode, errorMessage);
    }
}
