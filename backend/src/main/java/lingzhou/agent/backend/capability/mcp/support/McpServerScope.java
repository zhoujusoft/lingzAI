package lingzhou.agent.backend.capability.mcp.support;

import org.springframework.util.StringUtils;

public final class McpServerScope {

    public static final String INTERNAL = "INTERNAL";
    public static final String EXTERNAL = "EXTERNAL";

    private McpServerScope() {}

    public static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return INTERNAL;
        }
        String normalized = value.trim().toUpperCase();
        if (INTERNAL.equals(normalized) || EXTERNAL.equals(normalized)) {
            return normalized;
        }
        throw new IllegalStateException("Unsupported mcp server scope: " + value);
    }

    public static boolean isExternal(String value) {
        return EXTERNAL.equals(normalize(value));
    }
}
