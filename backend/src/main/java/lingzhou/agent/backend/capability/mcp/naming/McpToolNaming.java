package lingzhou.agent.backend.capability.mcp.naming;

import org.springframework.util.StringUtils;

public final class McpToolNaming {

    private static final String MCP_TOOL_PREFIX = "mcp.";
    private static final String MCP_SOURCE_PREFIX = "mcp:";

    private McpToolNaming() {}

    public static String source(String serverKey) {
        return MCP_SOURCE_PREFIX + sanitize(serverKey);
    }

    public static String internalToolName(String serverKey, String remoteToolName) {
        return MCP_TOOL_PREFIX + sanitize(serverKey) + "." + sanitize(remoteToolName);
    }

    public static boolean isMcpToolName(String toolName) {
        return StringUtils.hasText(toolName) && toolName.startsWith(MCP_TOOL_PREFIX);
    }

    public static String extractServerKey(String toolName) {
        if (!isMcpToolName(toolName)) {
            return "";
        }
        String remaining = toolName.substring(MCP_TOOL_PREFIX.length());
        int separator = remaining.indexOf('.');
        if (separator < 0) {
            return "";
        }
        return remaining.substring(0, separator);
    }

    public static String extractRemoteToolName(String toolName) {
        if (!isMcpToolName(toolName)) {
            return "";
        }
        String remaining = toolName.substring(MCP_TOOL_PREFIX.length());
        int separator = remaining.indexOf('.');
        if (separator < 0 || separator >= remaining.length() - 1) {
            return "";
        }
        return remaining.substring(separator + 1);
    }

    public static String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[_\\.\\-]+", "")
                .replaceAll("[_\\.\\-]+$", "");
    }
}
