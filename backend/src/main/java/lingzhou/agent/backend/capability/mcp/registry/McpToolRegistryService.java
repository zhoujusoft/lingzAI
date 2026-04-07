package lingzhou.agent.backend.capability.mcp.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lingzhou.agent.backend.capability.mcp.adapter.ExternalMcpAdapterRegistry;
import lingzhou.agent.backend.capability.mcp.adapter.ExternalMcpSession;
import lingzhou.agent.backend.capability.mcp.client.McpClientFactory;
import lingzhou.agent.backend.capability.mcp.naming.McpToolNaming;
import lingzhou.agent.backend.capability.mcp.support.McpServerScope;
import lingzhou.agent.backend.business.skill.domain.McpServer;
import lingzhou.agent.backend.business.skill.mapper.McpServerMapper;
import lingzhou.agent.backend.business.tool.domain.ToolCatalog;
import lingzhou.agent.backend.business.tool.mapper.ToolCatalogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class McpToolRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(McpToolRegistryService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ToolCatalogMapper toolCatalogMapper;
    private final McpServerMapper mcpServerMapper;
    private final McpClientFactory mcpClientFactory;
    private final ExternalMcpAdapterRegistry externalMcpAdapterRegistry;
    private final Map<String, ServerHandle> handles = new ConcurrentHashMap<>();

    public McpToolRegistryService(
            ToolCatalogMapper toolCatalogMapper,
            McpServerMapper mcpServerMapper,
            McpClientFactory mcpClientFactory,
            ExternalMcpAdapterRegistry externalMcpAdapterRegistry) {
        this.toolCatalogMapper = toolCatalogMapper;
        this.mcpServerMapper = mcpServerMapper;
        this.mcpClientFactory = mcpClientFactory;
        this.externalMcpAdapterRegistry = externalMcpAdapterRegistry;
    }

    public ToolCallback findByName(String toolName) {
        if (!McpToolNaming.isMcpToolName(toolName)) {
            return null;
        }
        ToolCatalog catalog = toolCatalogMapper.selectByToolName(toolName);
        if (catalog == null || !StringUtils.hasText(catalog.getSource())) {
            return null;
        }
        String serverKey = catalog.getSource().startsWith("mcp:")
                ? catalog.getSource().substring("mcp:".length())
                : McpToolNaming.extractServerKey(toolName);
        if (!StringUtils.hasText(serverKey)) {
            return null;
        }
        ToolCallback callback = lookup(toolName, serverKey, false);
        if (callback != null) {
            return callback;
        }
        return lookup(toolName, serverKey, true);
    }

    public void invalidate(String serverKey) {
        if (!StringUtils.hasText(serverKey)) {
            return;
        }
        ServerHandle handle = handles.remove(serverKey.trim());
        closeHandle(handle);
    }

    public void invalidateAll() {
        for (String serverKey : handles.keySet()) {
            invalidate(serverKey);
        }
    }

    private ToolCallback lookup(String toolName, String serverKey, boolean forceReload) {
        if (forceReload) {
            invalidate(serverKey);
        }
        ServerHandle handle = handles.computeIfAbsent(serverKey, this::createHandle);
        if (handle == null) {
            return null;
        }
        return Arrays.stream(handle.callbacks())
                .filter(callback -> callback != null && callback.getToolDefinition() != null)
                .filter(callback -> toolName.equals(callback.getToolDefinition().name()))
                .findFirst()
                .orElse(null);
    }

    private ServerHandle createHandle(String serverKey) {
        McpServer server = mcpServerMapper.selectByServerKey(serverKey);
        if (server == null || server.getEnabled() == null || server.getEnabled() != 1) {
            return null;
        }
        try {
            if (McpServerScope.isExternal(server.getServerScope())) {
                ExternalMcpSession session = externalMcpAdapterRegistry.openSession(server);
                ToolCallback[] callbacks = buildExternalCallbacks(server.getServerKey(), session, session.listTools());
                return new ServerHandle(session, callbacks);
            }
            McpSyncClient client = mcpClientFactory.createSyncClient(server, tools -> invalidate(serverKey));
            ToolCallback[] callbacks = mcpClientFactory.createToolCallbacks(serverKey, client);
            return new ServerHandle(client, callbacks);
        } catch (Exception ex) {
            logger.warn("初始化 MCP server 失败：serverKey={}, error={}", serverKey, ex.getMessage(), ex);
            return null;
        }
    }

    private void closeHandle(ServerHandle handle) {
        if (handle == null || handle.client() == null) {
            return;
        }
        if (handle.client() instanceof ExternalMcpSession externalSession) {
            try {
                externalSession.close();
            } catch (Exception ex) {
                logger.debug("关闭外部 MCP 会话失败：error={}", ex.getMessage(), ex);
            }
            return;
        }
        try {
            ((McpSyncClient) handle.client()).closeGracefully();
        } catch (Exception ex) {
            logger.debug("关闭 MCP client 失败：error={}", ex.getMessage(), ex);
        }
        try {
            ((McpSyncClient) handle.client()).close();
        } catch (Exception ex) {
            logger.debug("关闭 MCP client 失败：error={}", ex.getMessage(), ex);
        }
    }

    private ToolCallback[] buildExternalCallbacks(
            String serverKey, ExternalMcpSession session, java.util.List<McpSchema.Tool> remoteTools) {
        if (remoteTools == null || remoteTools.isEmpty()) {
            return new ToolCallback[0];
        }
        return remoteTools.stream()
                .filter(tool -> tool != null && StringUtils.hasText(tool.name()))
                .map(tool -> FunctionToolCallback.builder(
                                McpToolNaming.internalToolName(serverKey, tool.name()),
                                (Map<String, Object> arguments, org.springframework.ai.chat.model.ToolContext toolContext) -> {
                                    try {
                                        return session.callTool(tool.name(), arguments == null ? Map.of() : arguments);
                                    } catch (lingzhou.agent.backend.common.lzException.TaskException ex) {
                                        throw new IllegalStateException(ex.getMessage(), ex);
                                    }
                                })
                        .description(StringUtils.hasText(tool.description()) ? tool.description().trim() : "")
                        .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .inputSchema(toJsonSchemaString(serverKey, tool.inputSchema()))
                        .build())
                .toArray(ToolCallback[]::new);
    }

    private String toJsonSchemaString(String serverKey, McpSchema.JsonSchema schema) {
        if (schema == null) {
            return "{\"type\":\"object\",\"additionalProperties\":true}";
        }
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        if (StringUtils.hasText(schema.type())) {
            payload.put("type", schema.type());
        }
        if (schema.properties() != null && !schema.properties().isEmpty()) {
            payload.put("properties", schema.properties());
        }
        if (schema.required() != null && !schema.required().isEmpty()) {
            payload.put("required", schema.required());
        }
        if (schema.additionalProperties() != null) {
            payload.put("additionalProperties", schema.additionalProperties());
        }
        if (schema.defs() != null && !schema.defs().isEmpty()) {
            payload.put("$defs", schema.defs());
        }
        if (schema.definitions() != null && !schema.definitions().isEmpty()) {
            payload.put("definitions", schema.definitions());
        }
        try {
            return JSON.writeValueAsString(payload);
        } catch (Exception ex) {
            logger.warn("序列化外部 MCP 工具 schema 失败：serverKey={}, error={}", serverKey, ex.getMessage());
            return "{\"type\":\"object\",\"additionalProperties\":true}";
        }
    }

    private record ServerHandle(Object client, ToolCallback[] callbacks) {}
}
