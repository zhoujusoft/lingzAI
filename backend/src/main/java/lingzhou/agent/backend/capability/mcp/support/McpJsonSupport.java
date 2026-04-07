package lingzhou.agent.backend.capability.mcp.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class McpJsonSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private McpJsonSupport() {}

    public static Map<String, Object> parseJsonObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
