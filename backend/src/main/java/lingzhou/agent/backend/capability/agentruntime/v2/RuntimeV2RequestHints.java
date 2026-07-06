package lingzhou.agent.backend.capability.agentruntime.v2;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.Map;
import lingzhou.agent.backend.business.chat.runtime.ChatRuntimePreparedRequest;
import lingzhou.agent.backend.capability.agentruntime.v2.state.RuntimeV2Mode;
import org.springframework.util.StringUtils;

public final class RuntimeV2RequestHints {

    private RuntimeV2RequestHints() {}

    public static RuntimeV2Mode resolveMode(ChatRuntimePreparedRequest prepared) {
        String executionModeHint = readExecutionModeHint(prepared == null ? null : prepared.paramsJson());
        if ("TOOL".equalsIgnoreCase(executionModeHint)
                && prepared != null
                && prepared.toolCallbacks() != null
                && !prepared.toolCallbacks().isEmpty()) {
            return RuntimeV2Mode.REACT;
        }
        return RuntimeV2Mode.DIRECT;
    }

    public static String readExecutionModeHint(String paramsJson) {
        return readString(paramsJson, "executionModeHint");
    }

    public static boolean readBooleanFlag(String paramsJson, String key) {
        if (!StringUtils.hasText(paramsJson) || !StringUtils.hasText(key)) {
            return false;
        }
        try {
            Map<String, Object> payload = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                return false;
            }
            Object value = payload.get(key);
            return value != null
                    && "true".equalsIgnoreCase(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readString(String paramsJson, String key) {
        if (!StringUtils.hasText(paramsJson) || !StringUtils.hasText(key)) {
            return "";
        }
        try {
            Map<String, Object> payload = JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                return "";
            }
            Object value = payload.get(key);
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
