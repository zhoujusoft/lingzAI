package lingzhou.agent.backend.capability.mcp.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lingzhou.agent.backend.capability.mcp.support.McpServerScope;
import lingzhou.agent.backend.capability.mcp.support.McpJsonSupport;
import lingzhou.agent.backend.business.skill.domain.McpServer;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SseMessageEndpointExternalMcpAdapter implements ExternalMcpAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SseMessageEndpointExternalMcpAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String STREAMABLE_ACCEPT = "application/json, text/event-stream";
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    private static final String MCP_PROTOCOL_HEADER = "MCP-Protocol-Version";

    @Override
    public boolean supports(McpServer server) {
        return server != null
                && McpServerScope.isExternal(server.getServerScope())
                && isSse(server.getTransportType());
    }

    @Override
    public ExternalMcpSession openSession(McpServer server) throws TaskException {
        if (!supports(server)) {
            throw new TaskException("外部 SSE MCP 适配器不支持当前 server", TaskException.Code.UNKNOWN);
        }
        return new SseSession(server);
    }

    private static final class SseSession implements ExternalMcpSession {

        private final McpServer server;
        private final HttpClient httpClient;
        private final AtomicInteger nextId = new AtomicInteger(1);
        private final Map<Integer, CompletableFuture<Map<String, Object>>> pendingResponses = new ConcurrentHashMap<>();
        private final CompletableFuture<String> messageEndpointFuture = new CompletableFuture<>();
        private final CompletableFuture<Void> streamClosedFuture = new CompletableFuture<>();

        private volatile boolean initialized;
        private volatile boolean closed;
        private volatile InputStream streamBody;
        private volatile Thread readerThread;

        private SseSession(McpServer server) throws TaskException {
            this.server = server;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            openSseStream();
        }

        @Override
        public List<McpSchema.Tool> listTools() throws TaskException {
            ensureInitialized();
            Map<String, Object> response = invokeForMap("tools/list", Map.of());
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
            Map<String, Object> response = invokeForMap(
                    "tools/call",
                    Map.of(
                            "name", remoteToolName,
                            "arguments", arguments == null ? Map.of() : new LinkedHashMap<>(arguments)));
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
                return values.size() == 1 ? values.get(0) : values;
            }
            return resultMap;
        }

        @Override
        public void close() {
            closed = true;
            if (streamBody != null) {
                try {
                    streamBody.close();
                } catch (Exception ex) {
                    logger.debug("关闭外部 SSE MCP 流失败：serverKey={}, error={}", server.getServerKey(), ex.getMessage());
                }
            }
            if (readerThread != null) {
                readerThread.interrupt();
            }
            TaskException closedError = new TaskException("外部 SSE MCP 连接已关闭", TaskException.Code.UNKNOWN);
            pendingResponses.forEach((id, future) -> future.completeExceptionally(closedError));
            pendingResponses.clear();
        }

        private void ensureInitialized() throws TaskException {
            if (initialized) {
                return;
            }
            invokeForMap(
                    "initialize",
                    Map.of(
                            "protocolVersion", MCP_PROTOCOL_VERSION,
                            "capabilities", Map.of(),
                            "clientInfo", Map.of(
                                    "name", "lingzhou-agent-external-mcp",
                                    "version", "1.1.0")));
            invokeNotification("notifications/initialized", Map.of());
            initialized = true;
        }

        private void openSseStream() throws TaskException {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server.getEndpoint()))
                        .timeout(Duration.ofSeconds(30))
                        .header(HttpHeaders.ACCEPT, "text/event-stream")
                        .GET();
                applyAuth(builder, server);
                HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    throw new TaskException(
                            "外部 SSE MCP HTTP " + response.statusCode(),
                            TaskException.Code.UNKNOWN);
                }
                this.streamBody = response.body();
                Thread thread = new Thread(this::readSseLoop, "external-mcp-sse-" + server.getServerKey());
                thread.setDaemon(true);
                thread.start();
                this.readerThread = thread;
                waitForMessageEndpoint();
            } catch (TaskException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new TaskException("建立外部 SSE MCP 连接失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
            }
        }

        private void readSseLoop() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(streamBody, StandardCharsets.UTF_8))) {
                String event = null;
                StringBuilder data = new StringBuilder();
                String line;
                while (!closed && (line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        dispatchEvent(event, data.toString());
                        event = null;
                        data.setLength(0);
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        event = line.substring("event:".length()).trim();
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        appendData(data, line.substring("data:".length()).trim());
                        continue;
                    }
                    appendData(data, line.trim());
                }
            } catch (Exception ex) {
                if (!closed) {
                    logger.warn("读取外部 SSE MCP 流失败：serverKey={}, error={}", server.getServerKey(), ex.getMessage());
                    failPending("外部 SSE MCP 流读取失败：" + ex.getMessage());
                }
            } finally {
                streamClosedFuture.complete(null);
            }
        }

        private void dispatchEvent(String event, String data) {
            if (!StringUtils.hasText(event) && !StringUtils.hasText(data)) {
                return;
            }
            if ("endpoint".equals(event)) {
                String endpoint = resolveMessageEndpoint(data);
                if (StringUtils.hasText(endpoint)) {
                    messageEndpointFuture.complete(endpoint);
                }
                return;
            }
            if (!"message".equals(event) || !StringUtils.hasText(data)) {
                return;
            }
            try {
                Map<String, Object> payload = JSON.readValue(data, new TypeReference<Map<String, Object>>() {});
                Object id = payload.get("id");
                if (id instanceof Number number) {
                    CompletableFuture<Map<String, Object>> future = pendingResponses.remove(number.intValue());
                    if (future != null) {
                        future.complete(payload);
                    }
                }
            } catch (Exception ex) {
                logger.warn("解析外部 SSE MCP 消息失败：serverKey={}, error={}", server.getServerKey(), ex.getMessage());
            }
        }

        private Map<String, Object> invokeForMap(String method, Map<String, Object> params) throws TaskException {
            int id = nextId.getAndIncrement();
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            pendingResponses.put(id, future);
            try {
                postMessage(Map.of(
                        "jsonrpc", "2.0",
                        "id", id,
                        "method", method,
                        "params", params == null ? Map.of() : params));
                Map<String, Object> payload = future.get(30, TimeUnit.SECONDS);
                Object error = payload.get("error");
                if (error instanceof Map<?, ?> errorMap) {
                    throw new TaskException("外部 MCP 返回错误：" + summarizeError(errorMap.get("message")), TaskException.Code.UNKNOWN);
                }
                return payload;
            } catch (TaskException ex) {
                throw ex;
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof TaskException taskException) {
                    throw taskException;
                }
                throw new TaskException("外部 SSE MCP 响应执行失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
            } catch (TimeoutException ex) {
                throw new TaskException("外部 SSE MCP 响应超时：" + method, TaskException.Code.UNKNOWN);
            } catch (Exception ex) {
                throw new TaskException("外部 SSE MCP 响应失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
            } finally {
                pendingResponses.remove(id);
            }
        }

        private void invokeNotification(String method, Map<String, Object> params) throws TaskException {
            postMessage(Map.of(
                    "jsonrpc", "2.0",
                    "method", method,
                    "params", params == null ? Map.of() : params));
        }

        private void postMessage(Map<String, Object> payload) throws TaskException {
            String endpoint = waitForMessageEndpoint();
            try {
                String body = JSON.writeValueAsString(payload);
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(30))
                        .header(HttpHeaders.ACCEPT, STREAMABLE_ACCEPT)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .header(MCP_PROTOCOL_HEADER, MCP_PROTOCOL_VERSION)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                applyAuth(builder, server);
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status != 200 && status != 202) {
                    throw new TaskException(
                            "外部 SSE MCP HTTP " + status + "：" + summarizeError(response.body()),
                            TaskException.Code.UNKNOWN);
                }
            } catch (TaskException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new TaskException("外部 SSE MCP 请求失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
            }
        }

        private String waitForMessageEndpoint() throws TaskException {
            try {
                return messageEndpointFuture.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                throw new TaskException("外部 SSE MCP 未返回 messages endpoint", TaskException.Code.UNKNOWN);
            } catch (Exception ex) {
                throw new TaskException("外部 SSE MCP endpoint 获取失败：" + ex.getMessage(), TaskException.Code.UNKNOWN);
            }
        }

        private String resolveMessageEndpoint(String endpoint) {
            if (!StringUtils.hasText(endpoint)) {
                return "";
            }
            String trimmed = endpoint.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
            return URI.create(server.getEndpoint()).resolve(trimmed).toString();
        }

        private McpSchema.Tool toTool(Object rawTool) {
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

        private McpSchema.JsonSchema toJsonSchema(Object rawSchema) {
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

        private void failPending(String message) {
            TaskException error = new TaskException(message, TaskException.Code.UNKNOWN);
            pendingResponses.forEach((id, future) -> future.completeExceptionally(error));
            pendingResponses.clear();
            messageEndpointFuture.completeExceptionally(error);
        }

        private void appendData(StringBuilder builder, String value) {
            if (!StringUtils.hasText(value)) {
                return;
            }
            builder.append(value);
        }
    }

    private static void applyAuth(HttpRequest.Builder builder, McpServer server) {
        if (builder == null || server == null || !"BEARER_TOKEN".equalsIgnoreCase(trim(server.getAuthType()))) {
            return;
        }
        Map<String, Object> authConfig = McpJsonSupport.parseJsonObject(server.getAuthConfigJson());
        String token = authConfig == null ? "" : asText(authConfig.get("token"));
        if (StringUtils.hasText(token)) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
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

    private static boolean isSse(String value) {
        return "SSE".equalsIgnoreCase(trim(value));
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
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
