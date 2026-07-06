package lingzhou.agent.backend.capability.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

public final class ToolCallbackSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ToolCallbackSupport() {}

    public static List<ToolCallback> deduplicateByName(List<ToolCallback> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) {
            return List.of();
        }
        Map<String, ToolCallback> deduplicated = new LinkedHashMap<>();
        for (ToolCallback callback : callbacks) {
            String toolName = resolveToolName(callback);
            if (!StringUtils.hasText(toolName)) {
                continue;
            }
            deduplicated.putIfAbsent(toolName, callback);
        }
        return List.copyOf(deduplicated.values());
    }

    public static String resolveToolName(ToolCallback callback) {
        if (callback == null || callback.getToolDefinition() == null) {
            return null;
        }
        String toolName = callback.getToolDefinition().name();
        return StringUtils.hasText(toolName) ? toolName.trim() : null;
    }

    public static boolean acceptsEmptyArguments(ToolCallback callback) {
        String toolName = resolveToolName(callback);
        if ("listActiveSkills".equals(toolName)) {
            return true;
        }
        ToolDefinition definition = callback == null ? null : callback.getToolDefinition();
        if (definition == null || !StringUtils.hasText(definition.inputSchema())) {
            return false;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(definition.inputSchema());
            if (root == null || !root.isObject()) {
                return false;
            }
            JsonNode properties = root.path("properties");
            JsonNode required = root.path("required");
            boolean hasProperties = properties.isObject() && properties.size() > 0;
            boolean hasRequired = required.isArray() && required.size() > 0;
            return !hasProperties && !hasRequired;
        } catch (Exception ignored) {
            return false;
        }
    }
}
