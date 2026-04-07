package lingzhou.agent.backend.capability.mcp.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.capability.mcp.support.McpServerScope;
import lingzhou.agent.backend.capability.mcp.support.McpJsonSupport;
import lingzhou.agent.backend.business.skill.domain.McpServer;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class StatelessStreamableHttpExternalMcpAdapter implements ExternalMcpAdapter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String STREAMABLE_ACCEPT = "application/json, text/event-stream";
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    private static final String MCP_PROTOCOL_HEADER = "MCP-Protocol-Version";

    @Override
    public boolean supports(McpServer server) {
        return server != null
                && McpServerScope.isExternal(server.getServerScope())
                && isStreamableHttp(server.getTransportType());
    }

    @Override
    public ExternalMcpSession openSession(McpServer server) throws TaskException {
        if (!supports(server)) {
            throw new TaskException("外部 MCP 适配器不支持当前 server", TaskException.Code.UNKNOWN);
        }
        return new StatelessSession(server);
    }

    private static final class StatelessSession implements ExternalMcpSession {

        private final McpServer server;
        private final RestClient restClient;
        private boolean initialized;

        private StatelessSession(McpServer server) {
            this.server = server;
            this.restClient = RestClient.builder().build();
        }

        @Override
        public List<McpSchema.Tool> listTools() throws TaskException {
            ensureInitialized();
            Map<String, Object> response = invokeForMap(Map.of(
                    "jsonrpc", "2.0",
                    "id", 2,
                    "method", "tools/list",
                    "params", Map.of()));
            Object result = response.get("result");
            if (!(result instanceof Map<?, ?> resultMap)) {
                return List.of();
            }
            Object tools = resultMap.get("tools");
            if (!(tools instanceof List<?> rawTools)) {
                return List.of();
            }
            List<McpSchema.Tool> converted = new ArrayList<>(rawTools.size());
            for (Object rawTool : rawTools) {
                McpSchema.Tool tool = toTool(rawTool);
                if (tool != null && StringUtils.hasText(tool.name())) {
                    converted.add(tool);
                }
            }
            return converted;
        }

        @Override
        public Object callTool(String remoteToolName, Map<String, Object> arguments) throws TaskException {
            ensureInitialized();
            Map<String, Object> response = invokeForMap(Map.of(
                    "jsonrpc", "2.0",
                    "id", 3,
                    "method", "tools/call",
                    "params", Map.of(
                            "name", remoteToolName,
                            "arguments", arguments == null ? Map.of() : new LinkedHashMap<>(arguments))));
            Object result = response.get("result");
            if (!(result instanceof Map<?, ?> resultMap)) {
                return response;
            }
            Object structured = resultMap.get("structuredContent");
            if (structured != null) {
                return structured;
            }
            Object content = resultMap.get("content");
            if (content instanceof List<?> list && !list.isEmpty()) {
                List<Object> values = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Object text = map.get("text");
                        if (text != null) {
                            values.add(text);
                            continue;
                        }
                    }
                    values.add(item);
                }
                if (values.size() == 1) {
                    return values.get(0);
                }
                return values;
            }
            return resultMap;
        }

        private void ensureInitialized() throws TaskException {
            if (initialized) {
                return;
            }
            invokeForMap(Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "initialize",
                    "params", Map.of(
                            "protocolVersion", MCP_PROTOCOL_VERSION,
                            "capabilities", Map.of(),
                            "clientInfo", Map.of(
                                    "name", "lingzhou-agent-external-mcp",
                                    "version", "1.1.0"))));
            invokeForNotification(Map.of(
                    "jsonrpc", "2.0",
                    "method", "notifications/initialized",
                    "params", Map.of()));
            initialized = true;
        }

        private void invokeForNotification(Map<String, Object> payload) throws TaskException {
            post(payload);
        }

        private Map<String, Object> invokeForMap(Map<String, Object> payload) throws TaskException {
            String body = post(payload);
            if (!StringUtils.hasText(body)) {
                return Map.of();
            }
            try {
                Map<String, Object> parsed = JSON.readValue(extractJsonPayload(body), new TypeReference<Map<String, Object>>() {});
                Object error = parsed.get("error");
                if (error instanceof Map<?, ?> errorMap) {
                    throw new TaskException("外部 MCP 返回错误：" + summarizeError(errorMap.get("message")), TaskException.Code.UNKNOWN);
                }
                return parsed;
            } catch (TaskException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new TaskException("外部 MCP 响应解析失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
            }
        }

        private String post(Map<String, Object> payload) throws TaskException {
            try {
                byte[] body = restClient
                        .post()
                        .uri(server.getEndpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT, STREAMABLE_ACCEPT)
                        .header(MCP_PROTOCOL_HEADER, MCP_PROTOCOL_VERSION)
                        .headers(headers -> applyAuth(headers, server))
                        .body(payload)
                        .retrieve()
                        .body(byte[].class);
                if (body == null || body.length == 0) {
                    return "";
                }
                return new String(body, StandardCharsets.UTF_8);
            } catch (RestClientResponseException ex) {
                throw new TaskException(
                        "外部 MCP HTTP " + ex.getStatusCode().value() + "：" + summarizeError(ex.getResponseBodyAsString()),
                        TaskException.Code.UNKNOWN);
            } catch (Exception ex) {
                throw new TaskException("外部 MCP 请求失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
            }
        }

        private String extractJsonPayload(String body) {
            String trimmed = body == null ? "" : body.trim();
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("{")) {
                return trimmed;
            }
            if (!trimmed.contains("data:")) {
                return trimmed;
            }
            StringBuilder builder = new StringBuilder();
            boolean collectingData = false;
            for (String line : trimmed.split("\\R")) {
                if (line == null) {
                    continue;
                }
                String normalized = line;
                String lineValue = normalized.trim();
                if (!StringUtils.hasText(lineValue)) {
                    collectingData = false;
                    continue;
                }
                if (lineValue.startsWith("data:")) {
                    collectingData = true;
                    builder.append(lineValue.substring("data:".length()).trim());
                    continue;
                }
                if (lineValue.startsWith("event:") || lineValue.startsWith("id:") || lineValue.startsWith("retry:")) {
                    collectingData = false;
                    continue;
                }
                if (collectingData) {
                    builder.append(lineValue);
                }
            }
            return builder.length() == 0 ? trimmed : builder.toString();
        }

        private McpSchema.Tool toTool(Object rawTool) throws TaskException {
            if (!(rawTool instanceof Map<?, ?> rawMap)) {
                return null;
            }
            String name = asText(rawMap.get("name"));
            if (!StringUtils.hasText(name)) {
                return null;
            }
            McpSchema.JsonSchema jsonSchema = toJsonSchema(rawMap.get("inputSchema"));
            return McpSchema.Tool.builder()
                    .name(name)
                    .title(asText(rawMap.get("title")))
                    .description(asText(rawMap.get("description")))
                    .inputSchema(jsonSchema)
                    .build();
        }

        private McpSchema.JsonSchema toJsonSchema(Object rawSchema) throws TaskException {
            if (!(rawSchema instanceof Map<?, ?> schemaMap)) {
                return new McpSchema.JsonSchema("object", Map.of(), List.of(), Boolean.TRUE, Map.of(), Map.of());
            }
            Map<String, Object> props = toMap(schemaMap.get("properties"));
            List<String> required = toStringList(schemaMap.get("required"));
            Boolean additionalProperties = asBoolean(schemaMap.get("additionalProperties"));
            Map<String, Object> defs = toMap(schemaMap.get("$defs"));
            Map<String, Object> definitions = toMap(schemaMap.get("definitions"));
            return new McpSchema.JsonSchema(
                    defaultText(schemaMap.get("type"), "object"),
                    props,
                    required,
                    additionalProperties,
                    defs,
                    definitions);
        }

        private Map<String, Object> toMap(Object raw) {
            if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, value) -> converted.put(String.valueOf(key), value));
            return converted;
        }

        private List<String> toStringList(Object raw) {
            if (!(raw instanceof List<?> list) || list.isEmpty()) {
                return List.of();
            }
            List<String> converted = new ArrayList<>();
            for (Object item : list) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    converted.add(String.valueOf(item).trim());
                }
            }
            return converted;
        }

        private String defaultText(Object value, String defaultValue) {
            String text = asText(value);
            return StringUtils.hasText(text) ? text : defaultValue;
        }
    }

    private static void applyAuth(HttpHeaders headers, McpServer server) {
        if (headers == null || server == null || !"BEARER_TOKEN".equalsIgnoreCase(trim(server.getAuthType()))) {
            return;
        }
        Map<String, Object> authConfig = McpJsonSupport.parseJsonObject(server.getAuthConfigJson());
        String token = authConfig == null ? "" : asText(authConfig.get("token"));
        if (StringUtils.hasText(token)) {
            headers.setBearerAuth(token.trim());
        }
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static boolean isStreamableHttp(String value) {
        String normalized = trim(value).toUpperCase().replace('-', '_').replace(' ', '_');
        return "STREAMABLE_HTTP".equals(normalized);
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String summarizeError(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return "UNKNOWN";
        }
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
