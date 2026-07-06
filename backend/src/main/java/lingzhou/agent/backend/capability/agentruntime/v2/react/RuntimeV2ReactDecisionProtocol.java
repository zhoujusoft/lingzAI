package lingzhou.agent.backend.capability.agentruntime.v2.react;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuntimeV2ReactDecisionProtocol {

    private static final int USER_PREAMBLE_MESSAGE_MAX_LENGTH = 60;
    private static final String USER_PREAMBLE_MESSAGE_FIELD = "userPreambleMessage";
    private static final String LEGACY_USER_LEAD_MESSAGE_FIELD = "userLeadMessage";
    private static final String DECISION_JSON_MARKER = "<DECISION_JSON>";

    public ReactDecisionValidation validate(String rawText, Collection<String> availableToolNames) {
        if (!StringUtils.hasText(rawText)) {
            return ReactDecisionValidation.invalid("模型输出为空，无法解析为 Runtime V2 决策 JSON");
        }
        String normalized = extractDecisionJson(rawText);
        Map<String, Object> payload;
        try {
            payload = JSON.parseObject(normalized, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return ReactDecisionValidation.invalid("模型输出不是合法 JSON，请严格输出单个 JSON 对象");
        }
        if (payload == null || payload.isEmpty()) {
            return ReactDecisionValidation.invalid("模型输出的 JSON 为空对象，缺少必要字段");
        }

        String type = resolveDecisionType(payload);
        if (!"tool".equals(type)) {
            return ReactDecisionValidation.invalid("当前 REACT 决策 JSON 仅允许 type=tool；最终回答请直接输出正文");
        }

        String toolName = normalizeText(payload.get("toolName"));
        if (!StringUtils.hasText(toolName)) {
            return ReactDecisionValidation.invalid("当 type=tool 时，toolName 不能为空");
        }
        Set<String> toolNames = normalizeToolNames(availableToolNames);
        if (!toolNames.contains(toolName)) {
            return ReactDecisionValidation.invalid("toolName 不在当前可用工具列表中：" + toolName);
        }
        ShortTextValidation preambleValidation = validateUserPreambleMessage(payload);
        if (!preambleValidation.valid()) {
            return ReactDecisionValidation.invalid(preambleValidation.errorMessage());
        }
        String userPreambleMessage = StringUtils.hasText(preambleValidation.value())
                ? preambleValidation.value()
                : extractVisiblePreamble(rawText);
        Object argumentsValue = payload.get("arguments");
        if (argumentsValue == null) {
            return ReactDecisionValidation.valid(ReactDecision.toolCall(toolName, Map.of(), userPreambleMessage));
        }
        if (!(argumentsValue instanceof Map<?, ?> rawArguments)) {
            return ReactDecisionValidation.invalid("当 type=tool 时，arguments 必须是 JSON 对象");
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        rawArguments.forEach((key, value) -> {
            if (key != null) {
                arguments.put(String.valueOf(key), value);
            }
        });
        return ReactDecisionValidation.valid(ReactDecision.toolCall(toolName, arguments, userPreambleMessage));
    }

    public String stripCodeFence(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        if (!normalized.startsWith("```")) {
            return normalized;
        }
        normalized = normalized.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
        normalized = normalized.replaceFirst("\\s*```$", "");
        return normalized.trim();
    }

    public String extractDecisionJson(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        String normalized = stripCodeFence(rawText.trim());
        int markerIndex = normalized.indexOf(DECISION_JSON_MARKER);
        if (markerIndex >= 0) {
            return stripCodeFence(normalized
                    .substring(markerIndex + DECISION_JSON_MARKER.length())
                    .trim());
        }
        int directJsonIndex = findDecisionJsonStart(normalized);
        if (directJsonIndex >= 0) {
            return stripCodeFence(normalized.substring(directJsonIndex).trim());
        }
        return normalized;
    }

    public String extractVisiblePreamble(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        String normalized = stripCodeFence(rawText.trim());
        int markerIndex = normalized.indexOf(DECISION_JSON_MARKER);
        if (markerIndex >= 0) {
            return normalizeText(normalized.substring(0, markerIndex));
        }
        int directJsonIndex = findDecisionJsonStart(normalized);
        if (directJsonIndex > 0) {
            return normalizeText(normalized.substring(0, directJsonIndex));
        }
        return "";
    }

    public String decisionJsonMarker() {
        return DECISION_JSON_MARKER;
    }

    private int findDecisionJsonStart(String text) {
        if (!StringUtils.hasText(text)) {
            return -1;
        }
        int firstBrace = text.indexOf('{');
        if (firstBrace < 0) {
            return -1;
        }
        if (firstBrace == 0) {
            return 0;
        }
        String prefix = text.substring(0, firstBrace);
        if (prefix.isBlank()) {
            return firstBrace;
        }
        String trimmedPrefix = prefix.trim();
        if (trimmedPrefix.endsWith(":") || trimmedPrefix.endsWith("：")) {
            return firstBrace;
        }
        if (prefix.endsWith("\n") || prefix.endsWith("\r\n")) {
            return firstBrace;
        }
        return -1;
    }

    private Set<String> normalizeToolNames(Collection<String> availableToolNames) {
        if (availableToolNames == null || availableToolNames.isEmpty()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String availableToolName : availableToolNames) {
            String normalized = normalizeText(availableToolName);
            if (StringUtils.hasText(normalized)) {
                names.add(normalized);
            }
        }
        return Set.copyOf(names);
    }

    private String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String resolveDecisionType(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        String explicitType = normalizeText(payload.get("type")).toLowerCase();
        if (StringUtils.hasText(explicitType)) {
            return explicitType;
        }
        String toolName = normalizeText(payload.get("toolName"));
        return StringUtils.hasText(toolName) ? "tool" : "";
    }

    private ShortTextValidation validateOptionalShortText(
            Map<String, Object> payload, String fieldName, int maxLength) {
        if (payload == null || !StringUtils.hasText(fieldName) || !payload.containsKey(fieldName)) {
            return ShortTextValidation.valid("");
        }
        Object rawValue = payload.get(fieldName);
        if (rawValue == null) {
            return ShortTextValidation.valid("");
        }
        if (!(rawValue instanceof String)) {
            return ShortTextValidation.invalid(fieldName + " 必须是字符串");
        }
        String normalized = normalizeText(rawValue);
        if (normalized.length() > maxLength) {
            return ShortTextValidation.invalid(fieldName + " 长度不能超过 " + maxLength + " 个字符");
        }
        return ShortTextValidation.valid(normalized);
    }

    private ShortTextValidation validateUserPreambleMessage(Map<String, Object> payload) {
        ShortTextValidation preambleValidation =
                validateOptionalShortText(payload, USER_PREAMBLE_MESSAGE_FIELD, USER_PREAMBLE_MESSAGE_MAX_LENGTH);
        if (!preambleValidation.valid()) {
            return preambleValidation;
        }
        if (StringUtils.hasText(preambleValidation.value())
                || payload == null
                || payload.containsKey(USER_PREAMBLE_MESSAGE_FIELD)) {
            return preambleValidation;
        }
        ShortTextValidation legacyValidation =
                validateOptionalShortText(payload, LEGACY_USER_LEAD_MESSAGE_FIELD, USER_PREAMBLE_MESSAGE_MAX_LENGTH);
        if (!legacyValidation.valid()) {
            return ShortTextValidation.invalid(legacyValidation
                    .errorMessage()
                    .replace(LEGACY_USER_LEAD_MESSAGE_FIELD, USER_PREAMBLE_MESSAGE_FIELD));
        }
        return legacyValidation;
    }

    public record ReactDecision(
            String type, String answer, String toolName, Map<String, Object> arguments, String userPreambleMessage) {

        public static ReactDecision finalAnswer(String answer) {
            return new ReactDecision("final", answer == null ? "" : answer.trim(), "", Map.of(), "");
        }

        public static ReactDecision toolCall(
                String toolName, Map<String, Object> arguments, String userPreambleMessage) {
            return new ReactDecision(
                    "tool",
                    "",
                    toolName == null ? "" : toolName.trim(),
                    arguments == null || arguments.isEmpty() ? Map.of() : Map.copyOf(arguments),
                    userPreambleMessage == null ? "" : userPreambleMessage.trim());
        }
    }

    private record ShortTextValidation(boolean valid, String value, String errorMessage) {

        private static ShortTextValidation valid(String value) {
            return new ShortTextValidation(true, value == null ? "" : value.trim(), "");
        }

        private static ShortTextValidation invalid(String errorMessage) {
            return new ShortTextValidation(false, "", errorMessage == null ? "短文本字段校验失败" : errorMessage);
        }
    }

    public record ReactDecisionValidation(boolean valid, ReactDecision decision, String errorMessage) {

        public static ReactDecisionValidation valid(ReactDecision decision) {
            return new ReactDecisionValidation(true, decision, "");
        }

        public static ReactDecisionValidation invalid(String errorMessage) {
            return new ReactDecisionValidation(false, null, errorMessage == null ? "决策协议校验失败" : errorMessage);
        }
    }
}
