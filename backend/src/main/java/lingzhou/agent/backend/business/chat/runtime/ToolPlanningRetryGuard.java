package lingzhou.agent.backend.business.chat.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ToolPlanningRetryGuard {

    private static final List<String> PLANNING_PHRASES = List.of("我将查询", "我会查询", "让我使用", "正在获取", "我来调用", "稍后生成");
    private static final String RETRY_CORRECTION_PROMPT =
            """
            你刚才只是说明了计划，但没有发起工具调用。
            本轮任务必须通过工具完成。
            请立即调用合适工具，不要输出自然语言计划。
            """;
    private static final String RETRY_FAILURE_MESSAGE =
            "当前任务需要通过工具执行，但本轮连续两次都没有实际发起工具调用，暂时无法继续完成。";

    public boolean shouldRetry(ChatRuntimePreparedRequest prepared, List<Map<String, Object>> toolEvents, String text) {
        if (!requiresToolExecution(prepared)) {
            return false;
        }
        if (!StringUtils.hasText(text) || !hasPlanningText(text)) {
            return false;
        }
        return !hasToolExecution(toolEvents);
    }

    public boolean hasPlanningText(String text) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return PLANNING_PHRASES.stream().map(this::normalize).anyMatch(normalized::contains);
    }

    public String buildCorrectionPrompt(ChatRuntimePreparedRequest prepared) {
        String firstToolInstruction = resolveFirstToolInstruction(prepared);
        if (!StringUtils.hasText(firstToolInstruction)) {
            return RETRY_CORRECTION_PROMPT;
        }
        return RETRY_CORRECTION_PROMPT + "\n" + firstToolInstruction;
    }

    public String retryFailureMessage() {
        return RETRY_FAILURE_MESSAGE;
    }

    public boolean hasToolExecution(List<Map<String, Object>> toolEvents) {
        if (toolEvents == null || toolEvents.isEmpty()) {
            return false;
        }
        return toolEvents.stream().anyMatch(event -> {
            if (event == null) {
                return false;
            }
            String type = String.valueOf(event.get("type"));
            return "tool".equals(type) || "result".equals(type);
        });
    }

    public boolean shouldHoldPlanningText(ChatRuntimePreparedRequest prepared) {
        return requiresToolExecution(prepared);
    }

    private boolean requiresToolExecution(ChatRuntimePreparedRequest prepared) {
        if (prepared == null || prepared.toolCallbacks() == null || prepared.toolCallbacks().isEmpty()) {
            return false;
        }
        if (StringUtils.hasText(prepared.runtimeSkillName())) {
            return true;
        }
        if (!StringUtils.hasText(prepared.paramsJson())) {
            return false;
        }
        try {
            Map<String, Object> payload =
                    JSON.parseObject(prepared.paramsJson(), new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                return false;
            }
            Object executionModeHint = payload.get("executionModeHint");
            if (executionModeHint != null
                    && "TOOL".equalsIgnoreCase(String.valueOf(executionModeHint).trim())) {
                return true;
            }
            Object runtimeSkillState = payload.get("runtimeSkillState");
            if (runtimeSkillState instanceof Map<?, ?> runtimeSkillStateMap) {
                String selectedSkillHintRuntimeSkillName = trim(runtimeSkillStateMap.get("selectedSkillHintRuntimeSkillName"));
                if (StringUtils.hasText(selectedSkillHintRuntimeSkillName)) {
                    return true;
                }
            }
            return StringUtils.hasText(trim(payload.get("selectedSkillHintRuntimeSkillName")));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String resolveFirstToolInstruction(ChatRuntimePreparedRequest prepared) {
        String skillName = resolveSkillName(prepared);
        if (!StringUtils.hasText(skillName)) {
            return "";
        }
        return "第一步必须调用 `loadSkillContent(\"" + skillName + "\")`，不要先输出“我将查询”或类似计划说明。";
    }

    private String resolveSkillName(ChatRuntimePreparedRequest prepared) {
        if (prepared == null) {
            return "";
        }
        if (StringUtils.hasText(prepared.runtimeSkillName())) {
            return prepared.runtimeSkillName().trim();
        }
        if (!StringUtils.hasText(prepared.paramsJson())) {
            return "";
        }
        try {
            Map<String, Object> payload =
                    JSON.parseObject(prepared.paramsJson(), new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                return "";
            }
            Object runtimeSkillState = payload.get("runtimeSkillState");
            if (runtimeSkillState instanceof Map<?, ?> runtimeSkillStateMap) {
                String selected = trim(runtimeSkillStateMap.get("selectedSkillHintRuntimeSkillName"));
                if (StringUtils.hasText(selected)) {
                    return selected;
                }
                String current = trim(runtimeSkillStateMap.get("currentRuntimeSkillName"));
                if (StringUtils.hasText(current)) {
                    return current;
                }
            }
            String selected = trim(payload.get("selectedSkillHintRuntimeSkillName"));
            if (StringUtils.hasText(selected)) {
                return selected;
            }
            return trim(payload.get("currentRuntimeSkillName"));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String trim(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
