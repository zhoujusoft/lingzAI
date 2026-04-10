package lingzhou.agent.backend.business.skill.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lingzhou.agent.backend.capability.mcp.adapter.ExternalMcpAdapterRegistry;
import lingzhou.agent.backend.capability.mcp.adapter.ExternalMcpSession;
import lingzhou.agent.backend.capability.mcp.client.McpClientFactory;
import lingzhou.agent.backend.capability.mcp.naming.McpToolNaming;
import lingzhou.agent.backend.capability.mcp.publish.McpToolPublishService;
import lingzhou.agent.backend.capability.mcp.registry.McpToolRegistryService;
import lingzhou.agent.backend.capability.mcp.support.McpJsonSupport;
import lingzhou.agent.backend.capability.mcp.support.McpServerScope;
import lingzhou.agent.backend.business.skill.domain.McpServer;
import lingzhou.agent.backend.business.skill.mapper.McpServerMapper;
import lingzhou.agent.backend.business.skill.mapper.SkillToolBindingMapper;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class McpServerService {

    private static final Logger logger = LoggerFactory.getLogger(McpServerService.class);

    private static final Pattern SERVER_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final String REFRESH_STATUS_IDLE = "IDLE";
    private static final String REFRESH_STATUS_SUCCESS = "SUCCESS";
    private static final String REFRESH_STATUS_FAILED = "FAILED";
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final McpServerMapper mcpServerMapper;
    private final SkillToolBindingMapper skillToolBindingMapper;
    private final McpClientFactory mcpClientFactory;
    private final McpToolPublishService mcpToolPublishService;
    private final McpToolRegistryService mcpToolRegistryService;
    private final ExternalMcpAdapterRegistry externalMcpAdapterRegistry;

    public McpServerService(
            McpServerMapper mcpServerMapper,
            SkillToolBindingMapper skillToolBindingMapper,
            McpClientFactory mcpClientFactory,
            McpToolPublishService mcpToolPublishService,
            McpToolRegistryService mcpToolRegistryService,
            ExternalMcpAdapterRegistry externalMcpAdapterRegistry) {
        this.mcpServerMapper = mcpServerMapper;
        this.skillToolBindingMapper = skillToolBindingMapper;
        this.mcpClientFactory = mcpClientFactory;
        this.mcpToolPublishService = mcpToolPublishService;
        this.mcpToolRegistryService = mcpToolRegistryService;
        this.externalMcpAdapterRegistry = externalMcpAdapterRegistry;
    }

    public List<McpServerView> listServers() {
        List<McpServer> servers = mcpServerMapper.selectAllOrdered();
        Map<String, List<McpToolView>> toolsBySource = loadToolViewsBySources(servers);
        List<McpServerView> views = new ArrayList<>(servers.size());
        for (McpServer server : servers) {
            views.add(toView(
                    server,
                    toolsBySource.getOrDefault(McpToolNaming.source(server.getServerKey()), List.of())));
        }
        return views;
    }

    public McpServerView getServer(Long serverId) throws TaskException {
        McpServer server = requireServer(serverId);
        return toView(server, loadToolViews(McpToolNaming.source(server.getServerKey())));
    }

    public Set<String> listEnabledToolNames() {
        List<String> enabledSources = mcpServerMapper.selectEnabledOrdered().stream()
                .map(server -> McpToolNaming.source(server.getServerKey()))
                .toList();
        return mcpToolPublishService.listEnabledToolNamesBySources(enabledSources);
    }

    @Transactional(rollbackFor = Exception.class)
    public McpServerView createServer(CreateCommand command) throws TaskException {
        String serverKey = normalizeServerKey(command.serverKey());
        if (mcpServerMapper.selectByServerKey(serverKey) != null) {
            throw new TaskException("MCP serverKey 已存在", TaskException.Code.UNKNOWN);
        }
        McpServer server = new McpServer();
        applyCreate(server, command, serverKey);
        server.setLastRefreshStatus(REFRESH_STATUS_IDLE);
        server.setLastRefreshMessage("");
        mcpServerMapper.insert(server);
        return toView(server, List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public McpServerView updateServer(Long serverId, UpdateCommand command) throws TaskException {
        McpServer server = requireServer(serverId);
        applyUpdate(server, command);
        mcpServerMapper.updateById(server);
        mcpToolRegistryService.invalidate(server.getServerKey());
        return toView(server, loadToolViews(McpToolNaming.source(server.getServerKey())));
    }

    @Transactional(rollbackFor = Exception.class)
    public RefreshResult refreshServer(Long serverId) throws TaskException {
        McpServer server = requireServer(serverId);
        String source = McpToolNaming.source(server.getServerKey());
        try {
            List<McpSchema.Tool> tools;
            if (McpServerScope.isExternal(server.getServerScope())) {
                try (ExternalMcpSession session = externalMcpAdapterRegistry.openSession(server)) {
                    tools = session.listTools();
                }
            } else {
                try (McpSyncClient client = mcpClientFactory.createSyncClient(server, toolsChange -> {})) {
                    McpSchema.ListToolsResult listToolsResult = client.listTools();
                    tools = listToolsResult == null || listToolsResult.tools() == null
                            ? List.of()
                            : listToolsResult.tools();
                }
            }
            mcpToolPublishService.syncRemoteTools(server.getServerKey(), source, tools);
            server.setLastRefreshStatus(REFRESH_STATUS_SUCCESS);
            server.setLastRefreshMessage("同步 " + tools.size() + " 个 MCP 工具");
            server.setLastRefreshedAt(new Date());
            mcpServerMapper.updateById(server);
            mcpToolRegistryService.invalidate(server.getServerKey());
            logger.info("MCP tools refresh succeeded: serverKey={}, toolCount={}", server.getServerKey(), tools.size());
            return new RefreshResult(server.getId(), server.getServerKey(), tools.size());
        } catch (TaskException ex) {
            updateRefreshFailure(server, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            updateRefreshFailure(server, ex.getMessage());
            throw new TaskException("刷新 MCP server 失败：" + summarizeError(ex), TaskException.Code.UNKNOWN);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteServer(Long serverId) throws TaskException {
        McpServer server = requireServer(serverId);
        String source = McpToolNaming.source(server.getServerKey());
        List<String> toolNames = mcpToolPublishService.listToolNamesBySource(source);
        if (!toolNames.isEmpty()) {
            skillToolBindingMapper.deleteByToolNames(toolNames);
        }
        mcpToolPublishService.deleteBySource(source);
        mcpServerMapper.deleteById(server.getId());
        mcpToolRegistryService.invalidate(server.getServerKey());
        logger.info("MCP server deleted: serverKey={}, toolCount={}", server.getServerKey(), toolNames.size());
    }

    static Map<String, Object> parseJsonObject(String json) {
        return McpJsonSupport.parseJsonObject(json);
    }

    private void applyCreate(McpServer server, CreateCommand command, String serverKey) throws TaskException {
        server.setServerKey(serverKey);
        server.setDisplayName(normalizeRequired(command.displayName(), "MCP server 展示名称不能为空"));
        server.setDescription(normalizeText(command.description()));
        server.setServerScope(normalizeServerScope(command.serverScope()));
        server.setTransportType(normalizeTransportType(command.transportType()));
        server.setEndpoint(validateEndpoint(command.endpoint()));
        server.setAuthType(normalizeAuthType(command.authType()));
        server.setAuthConfigJson(validateAuthConfig(server.getAuthType(), command.authConfigJson()));
        server.setEnabled(Boolean.FALSE.equals(command.enabled()) ? 0 : 1);
    }

    private void applyUpdate(McpServer server, UpdateCommand command) throws TaskException {
        server.setDisplayName(normalizeRequired(command.displayName(), "MCP server 展示名称不能为空"));
        server.setDescription(normalizeText(command.description()));
        server.setServerScope(normalizeServerScope(command.serverScope()));
        server.setTransportType(normalizeTransportType(command.transportType()));
        server.setEndpoint(validateEndpoint(command.endpoint()));
        server.setAuthType(normalizeAuthType(command.authType()));
        String nextAuthConfigJson = command.authConfigJson();
        if (StringUtils.hasText(nextAuthConfigJson)) {
            server.setAuthConfigJson(validateAuthConfig(server.getAuthType(), nextAuthConfigJson));
        } else if ("NONE".equals(server.getAuthType())) {
            server.setAuthConfigJson("");
        } else if (!StringUtils.hasText(server.getAuthConfigJson())) {
            throw new TaskException("Bearer Token 凭证不能为空", TaskException.Code.UNKNOWN);
        }
        server.setEnabled(Boolean.FALSE.equals(command.enabled()) ? 0 : 1);
    }

    private void updateRefreshFailure(McpServer server, String message) {
        if (server == null || server.getId() == null) {
            return;
        }
        server.setLastRefreshStatus(REFRESH_STATUS_FAILED);
        server.setLastRefreshMessage(summarizeError(message));
        mcpServerMapper.updateById(server);
        mcpToolRegistryService.invalidate(server.getServerKey());
        logger.warn("MCP tools refresh failed: serverKey={}, error={}", server.getServerKey(), summarizeError(message));
    }

    private McpServer requireServer(Long serverId) throws TaskException {
        if (serverId == null || serverId <= 0) {
            throw new TaskException("MCP server ID 无效", TaskException.Code.UNKNOWN);
        }
        McpServer server = mcpServerMapper.selectById(serverId);
        if (server == null) {
            throw new TaskException("MCP server 不存在", TaskException.Code.UNKNOWN);
        }
        return server;
    }

    private Map<String, List<McpToolView>> loadToolViewsBySources(List<McpServer> servers) {
        if (servers == null || servers.isEmpty()) {
            return Map.of();
        }
        List<String> sources = servers.stream()
                .map(McpServer::getServerKey)
                .filter(StringUtils::hasText)
                .map(McpToolNaming::source)
                .toList();
        if (sources.isEmpty()) {
            return Map.of();
        }
        return toToolViewMap(mcpToolPublishService.loadToolViewsBySources(sources));
    }

    private List<McpToolView> loadToolViews(String source) {
        return mcpToolPublishService.loadToolViews(source).stream()
                .map(this::toToolView)
                .toList();
    }

    private Map<String, List<McpToolView>> toToolViewMap(Map<String, List<McpToolPublishService.McpToolView>> source) {
        Map<String, List<McpToolView>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<McpToolPublishService.McpToolView>> entry : source.entrySet()) {
            result.put(
                    entry.getKey(),
                    entry.getValue().stream().map(this::toToolView).toList());
        }
        return result;
    }

    private McpToolView toToolView(McpToolPublishService.McpToolView item) {
        return new McpToolView(
                item.id(),
                item.toolName(),
                item.remoteToolName(),
                item.displayName(),
                item.description(),
                item.bindable(),
                item.updatedAt());
    }

    private McpServerView toView(McpServer server, List<McpToolView> tools) {
        List<McpToolView> safeTools = tools == null ? List.of() : List.copyOf(tools);
        return new McpServerView(
                server.getId(),
                server.getServerKey(),
                server.getDisplayName(),
                server.getDescription(),
                normalizeServerScopeForView(server.getServerScope()),
                server.getTransportType(),
                server.getEndpoint(),
                server.getAuthType(),
                StringUtils.hasText(server.getAuthConfigJson()),
                server.getEnabled() != null && server.getEnabled() == 1,
                server.getLastRefreshStatus(),
                server.getLastRefreshMessage(),
                server.getLastRefreshedAt(),
                server.getCreatedAt(),
                server.getUpdatedAt(),
                safeTools.size(),
                safeTools);
    }

    private static String normalizeServerKey(String value) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException("MCP serverKey 不能为空", TaskException.Code.UNKNOWN);
        }
        String normalized = value.trim();
        if (!SERVER_KEY_PATTERN.matcher(normalized).matches()) {
            throw new TaskException("MCP serverKey 仅支持字母、数字、点、下划线和中划线", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private static String normalizeTransportType(String value) throws TaskException {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : "STREAMABLE_HTTP";
        if (!Objects.equals(normalized, "STREAMABLE_HTTP") && !Objects.equals(normalized, "SSE")) {
            throw new TaskException("MCP transport 仅支持 STREAMABLE_HTTP 或 SSE", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private static String normalizeServerScope(String value) throws TaskException {
        try {
            return McpServerScope.normalize(value);
        } catch (IllegalStateException ex) {
            throw new TaskException("MCP server_scope 仅支持 INTERNAL 或 EXTERNAL", TaskException.Code.UNKNOWN);
        }
    }

    private static String normalizeServerScopeForView(String value) {
        try {
            return McpServerScope.normalize(value);
        } catch (Exception ex) {
            return McpServerScope.INTERNAL;
        }
    }

    private static String normalizeAuthType(String value) throws TaskException {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : "NONE";
        if (!Objects.equals(normalized, "NONE") && !Objects.equals(normalized, "BEARER_TOKEN")) {
            throw new TaskException("MCP authType 仅支持 NONE 或 BEARER_TOKEN", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private static String validateEndpoint(String endpoint) throws TaskException {
        if (!StringUtils.hasText(endpoint)) {
            throw new TaskException("MCP endpoint 不能为空", TaskException.Code.UNKNOWN);
        }
        String normalized = endpoint.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new TaskException("MCP endpoint 仅支持 http/https", TaskException.Code.UNKNOWN);
        }
        return normalized;
    }

    private static String validateAuthConfig(String authType, String authConfigJson) throws TaskException {
        if ("NONE".equals(authType)) {
            return "";
        }
        Map<String, Object> authConfig = parseJsonObject(authConfigJson);
        String token = authConfig == null ? "" : String.valueOf(authConfig.getOrDefault("token", "")).trim();
        if (!StringUtils.hasText(token)) {
            throw new TaskException("Bearer Token 凭证不能为空", TaskException.Code.UNKNOWN);
        }
        return String.format("{\"token\":\"%s\"}", token.replace("\\", "\\\\").replace("\"", "\\\""));
    }

    private static String normalizeRequired(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static String summarizeError(Exception ex) {
        return summarizeError(ex == null ? "" : ex.getMessage());
    }

    private static String summarizeError(String message) {
        if (!StringUtils.hasText(message)) {
            return "UNKNOWN";
        }
        String normalized = message.trim().replaceAll("[\\r\\n]+", " ");
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    public record McpServerView(
            Long id,
            String serverKey,
            String displayName,
            String description,
            String serverScope,
            String transportType,
            String endpoint,
            String authType,
            boolean hasAuthConfig,
            boolean enabled,
            String lastRefreshStatus,
            String lastRefreshMessage,
            Date lastRefreshedAt,
            Date createdAt,
            Date updatedAt,
            int toolCount,
            List<McpToolView> tools) {}

    public record McpToolView(
            Long id,
            String toolName,
            String remoteToolName,
            String displayName,
            String description,
            boolean bindable,
            Date updatedAt) {}

    public record CreateCommand(
            String serverKey,
            String displayName,
            String description,
            String serverScope,
            String transportType,
            String endpoint,
            String authType,
            String authConfigJson,
            Boolean enabled) {}

    public record UpdateCommand(
            String displayName,
            String description,
            String serverScope,
            String transportType,
            String endpoint,
            String authType,
            String authConfigJson,
            Boolean enabled) {}

    public record RefreshResult(Long serverId, String serverKey, int toolCount) {}
}
