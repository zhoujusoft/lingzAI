package lingzhou.agent.backend.capability.mcp.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lingzhou.agent.backend.capability.mcp.naming.McpToolNaming;
import lingzhou.agent.backend.capability.mcp.support.McpJsonSupport;
import lingzhou.agent.backend.business.skill.domain.McpServer;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class McpClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String STREAMABLE_HTTP_ACCEPT = "application/json, text/event-stream";

    public McpSyncClient createSyncClient(McpServer server, Consumer<List<McpSchema.Tool>> toolsChangeConsumer)
            throws TaskException {
        if (server == null) {
            throw new TaskException("MCP server 不存在", TaskException.Code.UNKNOWN);
        }
        String endpoint = normalizeRequired(server.getEndpoint(), "MCP server endpoint 不能为空");
        McpClientTransport transport = switch (normalizeTransportType(server.getTransportType())) {
            case "SSE" -> HttpClientSseClientTransport.builder(endpoint)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .httpRequestCustomizer(buildRequestCustomizer(server))
                    .build();
            case "STREAMABLE_HTTP" -> HttpClientStreamableHttpTransport.builder(endpoint)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .httpRequestCustomizer(buildRequestCustomizer(server))
                    .build();
            default -> throw new TaskException("不支持的 MCP transport 类型", TaskException.Code.UNKNOWN);
        };
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .initializationTimeout(INITIALIZATION_TIMEOUT)
                .clientInfo(new McpSchema.Implementation(
                        normalizeRequired(server.getServerKey(), "MCP server key 不能为空"),
                        StringUtils.hasText(server.getDisplayName()) ? server.getDisplayName().trim() : server.getServerKey(),
                        "1.1.0"))
                .capabilities(McpSchema.ClientCapabilities.builder().build())
                .toolsChangeConsumer(toolsChangeConsumer)
                .build();
        client.initialize();
        return client;
    }

    public ToolCallback[] createToolCallbacks(String serverKey, McpSyncClient client) {
        McpToolNamePrefixGenerator prefixGenerator = (McpConnectionInfo connectionInfo, McpSchema.Tool tool) ->
                McpToolNaming.internalToolName(serverKey, tool.name());
        return SyncMcpToolCallbackProvider.builder()
                .addMcpClient(client)
                .toolNamePrefixGenerator(prefixGenerator)
                .build()
                .getToolCallbacks();
    }

    private McpSyncHttpClientRequestCustomizer buildRequestCustomizer(McpServer server) {
        String transportType = normalizeTransportType(server.getTransportType());
        String authType = normalizeTransportType(server.getAuthType());
        Map<String, Object> authConfig = McpJsonSupport.parseJsonObject(server.getAuthConfigJson());
        String token = authConfig == null ? "" : toText(authConfig.get("token"));
        return (requestBuilder, method, uri, body, transportContext) -> {
            if ("STREAMABLE_HTTP".equals(transportType)) {
                requestBuilder.setHeader("Accept", STREAMABLE_HTTP_ACCEPT);
            }
            if (StringUtils.hasText(token)) {
                requestBuilder.header("Authorization", "Bearer " + token.trim());
            }
        };
    }

    private static String normalizeTransportType(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : "";
    }

    private static String normalizeRequired(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private static String toText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
