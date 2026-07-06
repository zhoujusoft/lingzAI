package lingzhou.agent.backend.business.chat.execution.tool;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionResult;

public final class RuntimeToolResultFormatter {

    private RuntimeToolResultFormatter() {}

    public static String format(RuntimeExecutionResult result) {
        if (result == null) {
            return JSON.toJSONString(Map.of("success", false, "error", "Runtime result is null"));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", result.success());
        payload.put("action", result.action() == null ? null : result.action().name());
        if (result.textOutput() != null) {
            payload.put("textOutput", result.textOutput());
        }
        if (result.data() != null && !result.data().isEmpty()) {
            payload.put("data", result.data());
        }
        if (!result.success()) {
            payload.put("errorCode", result.errorCode());
            payload.put("errorMessage", result.errorMessage());
        }
        return JSON.toJSONString(payload);
    }
}
