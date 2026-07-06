package lingzhou.agent.backend.capability.sandbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

@Service
public class DockerGuiSandboxService {

    private static final Logger logger = LoggerFactory.getLogger(DockerGuiSandboxService.class);
    private static final String DEFAULT_IMAGE =
            "agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/runtime-sandbox-browser:latest";
    private static final Path WORKSPACE_USERS_ROOT = Path.of(System.getProperty(
            "lingzhou.sandbox.workspace-users-root", "/Users/xiehb/workspace/lingzhou-agent/workspaces/users"));
    private static final DateTimeFormatter SCREENSHOT_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());
    private static final Duration DOCKER_COMMAND_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration TOOL_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, SandboxSession> sessions = new ConcurrentHashMap<>();

    public DockerGuiSandboxService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public SandboxSessionView start(Long userId, SandboxStartRequest request) throws TaskException {
        requireUser(userId);
        String sessionId = randomToken(16);
        String runtimeToken = randomToken(32);
        int hostPort = allocatePort();
        String imageName = StringUtils.hasText(request == null ? null : request.imageName())
                ? request.imageName().trim()
                : DEFAULT_IMAGE;
        String containerName = "lingzhou-sandbox-test-" + sessionId;
        Path workspacePath = createWorkspacePath(userId, sessionId);

        List<String> command = List.of(
                "docker",
                "run",
                "-d",
                "--name",
                containerName,
                "-p",
                hostPort + ":80",
                "-e",
                "SECRET_TOKEN=" + runtimeToken,
                "-v",
                workspacePath.toAbsolutePath() + ":/workspace",
                "--shm-size=2g",
                imageName);

        logger.info(
                "启动 Docker GUI 测试容器：userId={}, sessionId={}, image={}, port={}",
                userId,
                sessionId,
                imageName,
                hostPort);
        CommandResult result = runCommand(command, DOCKER_COMMAND_TIMEOUT);
        if (result.exitCode() != 0) {
            throw new TaskException("Docker 容器启动失败：" + truncate(result.output()), TaskException.Code.UNKNOWN);
        }

        String containerId = firstLine(result.output());
        SandboxSession session = new SandboxSession(
                sessionId,
                userId,
                containerId,
                containerName,
                imageName,
                runtimeToken,
                hostPort,
                workspacePath,
                "STARTING",
                Instant.now(),
                "",
                "",
                "");
        sessions.put(sessionId, session);

        try {
            waitUntilHealthy(session);
            session.status = "RUNNING";
            logger.info("Docker GUI 测试容器已就绪：userId={}, sessionId={}, containerId={}", userId, sessionId, containerId);
        } catch (TaskException ex) {
            session.status = "ERROR";
            session.lastToolResult = ex.getMessage();
            logger.warn("Docker GUI 测试容器健康检查失败：userId={}, sessionId={}, error={}", userId, sessionId, ex.getMessage());
        }

        return toView(session);
    }

    public SandboxSessionView getInfo(Long userId, String sessionId) throws TaskException {
        SandboxSession session = requireSession(userId, sessionId);
        session.status = inspectStatus(session);
        return toView(session);
    }

    public SandboxSessionView navigate(Long userId, String sessionId, SandboxNavigateRequest request)
            throws TaskException {
        String url = normalizeUrl(request == null ? null : request.url());
        return callTool(userId, sessionId, new SandboxToolRequest("browser_navigate", Map.of("url", url)));
    }

    public SandboxSessionView openBaidu(Long userId, String sessionId) throws TaskException {
        return callTool(
                userId, sessionId, new SandboxToolRequest("browser_navigate", Map.of("url", "https://www.baidu.com")));
    }

    public SandboxSessionView takeScreenshot(Long userId, String sessionId) throws TaskException {
        return callTool(
                userId, sessionId, new SandboxToolRequest("browser_take_screenshot", Map.of("raw", Boolean.FALSE)));
    }

    public SandboxSessionView snapshot(Long userId, String sessionId) throws TaskException {
        return callTool(userId, sessionId, new SandboxToolRequest("browser_snapshot", Map.of()));
    }

    public SandboxSessionView callTool(Long userId, String sessionId, SandboxToolRequest request) throws TaskException {
        SandboxSession session = requireSession(userId, sessionId);
        String toolName = normalizeRequired(request == null ? null : request.toolName(), "工具名不能为空");
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        Map<String, Object> payload = new HashMap<>();
        payload.put("tool_name", toolName);
        payload.put("arguments", arguments);

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new TaskException("工具请求序列化失败：" + ex.getMessage(), TaskException.Code.UNKNOWN, ex);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl(session) + "/mcp/call_tool"))
                .timeout(TOOL_TIMEOUT)
                .header("Authorization", "Bearer " + session.runtimeToken)
                .header("Content-Type", "application/json")
                .header("x-agentscope-runtime-session-id", "s" + session.sessionId)
                .header("x-agentrun-session-id", "s" + session.sessionId)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            session.status = inspectStatus(session);
            if (response.statusCode() >= 400) {
                throw new TaskException(
                        "调用沙箱工具失败：HTTP " + response.statusCode() + " " + truncate(response.body()),
                        TaskException.Code.UNKNOWN);
            }
            session.lastToolResult = response.body();
            if ("browser_take_screenshot".equals(toolName)) {
                saveScreenshot(session, response.body());
            }
            logger.info("Docker GUI 测试工具调用完成：userId={}, sessionId={}, tool={}", userId, sessionId, toolName);
            return toView(session);
        } catch (IOException ex) {
            throw new TaskException("调用沙箱工具失败：" + ex.getMessage(), TaskException.Code.UNKNOWN, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TaskException("调用沙箱工具被中断", TaskException.Code.UNKNOWN, ex);
        }
    }

    public SandboxSessionView stop(Long userId, String sessionId) throws TaskException {
        SandboxSession session = requireSession(userId, sessionId);
        CommandResult result = runCommand(List.of("docker", "rm", "-f", session.containerName), Duration.ofSeconds(30));
        if (result.exitCode() != 0) {
            throw new TaskException("停止 Docker 容器失败：" + truncate(result.output()), TaskException.Code.UNKNOWN);
        }
        session.status = "STOPPED";
        session.lastToolResult = result.output();
        sessions.remove(sessionId);
        logger.info(
                "Docker GUI 测试容器已停止：userId={}, sessionId={}, containerName={}",
                userId,
                sessionId,
                session.containerName);
        return toView(session);
    }

    private void waitUntilHealthy(SandboxSession session) throws TaskException {
        long deadline = System.nanoTime() + HEALTH_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl(session) + "/healthz"))
                        .timeout(Duration.ofSeconds(2))
                        .header("Authorization", "Bearer " + session.runtimeToken)
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException ex) {
                // Container is still warming up.
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new TaskException("等待沙箱健康检查被中断", TaskException.Code.UNKNOWN, ex);
            }
            sleepOneSecond();
        }
        throw new TaskException("沙箱服务未在 " + HEALTH_TIMEOUT.toSeconds() + " 秒内就绪", TaskException.Code.UNKNOWN);
    }

    private String inspectStatus(SandboxSession session) {
        try {
            CommandResult result = runCommand(
                    List.of("docker", "inspect", "-f", "{{.State.Status}}", session.containerName),
                    Duration.ofSeconds(10));
            if (result.exitCode() == 0 && StringUtils.hasText(result.output())) {
                return firstLine(result.output()).toUpperCase();
            }
        } catch (TaskException ex) {
            logger.warn("读取 Docker GUI 测试容器状态失败：sessionId={}, error={}", session.sessionId, ex.getMessage());
        }
        return session.status;
    }

    private void saveScreenshot(SandboxSession session, String responseBody) throws TaskException {
        ImagePayload imagePayload = extractImagePayload(responseBody);
        Path screenshotDir = session.workspacePath.resolve("screenshots");
        String fileName =
                "screenshot-" + SCREENSHOT_TIMESTAMP_FORMAT.format(Instant.now()) + "." + imagePayload.extension();
        Path screenshotPath = screenshotDir.resolve(fileName);
        try {
            Files.createDirectories(screenshotDir);
            Files.write(screenshotPath, imagePayload.bytes());
        } catch (IOException ex) {
            throw new TaskException("保存截图失败：" + ex.getMessage(), TaskException.Code.UNKNOWN, ex);
        }
        session.lastScreenshotPath = screenshotPath.toString();
        session.lastScreenshotMimeType = imagePayload.mimeType();
        session.lastToolResult = "截图已保存："
                + screenshotPath
                + "\n类型："
                + imagePayload.mimeType()
                + "\n大小："
                + imagePayload.bytes().length
                + " bytes";
    }

    private ImagePayload extractImagePayload(String responseBody) throws TaskException {
        try {
            JsonNode content = objectMapper.readTree(responseBody).path("content");
            if (!content.isArray()) {
                throw new TaskException("截图工具未返回图片内容", TaskException.Code.UNKNOWN);
            }
            for (JsonNode item : content) {
                if (!"image".equals(item.path("type").asText())) {
                    continue;
                }
                String data = item.path("data").asText("");
                String mimeType = item.path("mimeType").asText("");
                ImageData imageData = decodeImageData(data, mimeType);
                return new ImagePayload(
                        imageData.bytes(),
                        normalizeMimeType(imageData.mimeType(), imageData.bytes()),
                        extensionFor(imageData.mimeType(), imageData.bytes()));
            }
            throw new TaskException("截图工具未返回图片内容", TaskException.Code.UNKNOWN);
        } catch (JsonProcessingException ex) {
            throw new TaskException("解析截图返回失败：" + ex.getMessage(), TaskException.Code.UNKNOWN, ex);
        } catch (IllegalArgumentException ex) {
            throw new TaskException("解析截图图片失败：" + ex.getMessage(), TaskException.Code.UNKNOWN, ex);
        }
    }

    private ImageData decodeImageData(String data, String mimeType) {
        String normalizedData = StringUtils.hasText(data) ? data.trim() : "";
        String normalizedMimeType = StringUtils.hasText(mimeType) ? mimeType.trim() : "";
        if (normalizedData.startsWith("data:")) {
            int commaIndex = normalizedData.indexOf(',');
            if (commaIndex > 0) {
                String metadata = normalizedData.substring(5, commaIndex);
                int separatorIndex = metadata.indexOf(';');
                normalizedMimeType = separatorIndex > 0 ? metadata.substring(0, separatorIndex) : metadata;
                normalizedData = normalizedData.substring(commaIndex + 1);
            }
        }
        if (!StringUtils.hasText(normalizedData)) {
            throw new IllegalArgumentException("图片数据为空");
        }
        byte[] bytes = Base64.getDecoder().decode(normalizedData.replaceAll("\\s+", ""));
        if (bytes.length == 0) {
            throw new IllegalArgumentException("图片数据为空");
        }
        return new ImageData(bytes, normalizedMimeType);
    }

    private String normalizeMimeType(String mimeType, byte[] bytes) {
        if (StringUtils.hasText(mimeType)) {
            return mimeType;
        }
        if (isJpeg(bytes)) {
            return "image/jpeg";
        }
        if (isPng(bytes)) {
            return "image/png";
        }
        return "application/octet-stream";
    }

    private String extensionFor(String mimeType, byte[] bytes) {
        String normalizedMimeType = normalizeMimeType(mimeType, bytes);
        if ("image/png".equalsIgnoreCase(normalizedMimeType)) {
            return "png";
        }
        if ("image/jpeg".equalsIgnoreCase(normalizedMimeType) || "image/jpg".equalsIgnoreCase(normalizedMimeType)) {
            return "jpg";
        }
        return "bin";
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a;
    }

    private CommandResult runCommand(List<String> command, Duration timeout) throws TaskException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TaskException("命令执行超时：" + describeCommand(command), TaskException.Code.UNKNOWN);
            }
            String output = outputFuture.get(5, TimeUnit.SECONDS);
            return new CommandResult(process.exitValue(), output.trim());
        } catch (TaskException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new TaskException(
                    "执行 Docker 命令失败，请确认 Docker CLI 可用：" + ex.getMessage(), TaskException.Code.UNKNOWN, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TaskException("Docker 命令执行被中断", TaskException.Code.UNKNOWN, ex);
        } catch (Exception ex) {
            throw new TaskException("读取 Docker 命令输出失败：" + ex.getMessage(), TaskException.Code.UNKNOWN, ex);
        }
    }

    private String readOutput(Process process) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (IOException ex) {
            builder.append(ex.getMessage());
        }
        return builder.toString();
    }

    private String describeCommand(List<String> command) {
        return command.stream()
                .map(part -> part.startsWith("SECRET_TOKEN=") ? "SECRET_TOKEN=***" : part)
                .collect(Collectors.joining(" "));
    }

    private SandboxSession requireSession(Long userId, String sessionId) throws TaskException {
        requireUser(userId);
        String normalizedSessionId = normalizeRequired(sessionId, "沙箱会话不能为空");
        SandboxSession session = sessions.get(normalizedSessionId);
        if (session == null || !userId.equals(session.userId)) {
            throw new TaskException("沙箱会话不存在或已停止", TaskException.Code.UNKNOWN);
        }
        return session;
    }

    private void requireUser(Long userId) throws TaskException {
        if (userId == null || userId <= 0) {
            throw new TaskException("未授权", TaskException.Code.UNKNOWN);
        }
    }

    private String normalizeRequired(String value, String message) throws TaskException {
        if (!StringUtils.hasText(value)) {
            throw new TaskException(message, TaskException.Code.UNKNOWN);
        }
        return value.trim();
    }

    private String normalizeUrl(String url) throws TaskException {
        String normalized = normalizeRequired(url, "URL 不能为空");
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        return normalized;
    }

    private int allocatePort() throws TaskException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new TaskException("分配 Docker 映射端口失败：" + ex.getMessage(), TaskException.Code.UNKNOWN, ex);
        }
    }

    private Path createWorkspacePath(Long userId, String sessionId) throws TaskException {
        try {
            Path path = WORKSPACE_USERS_ROOT
                    .resolve(String.valueOf(userId))
                    .resolve("sessions")
                    .resolve(sessionId);
            Files.createDirectories(path);
            return path;
        } catch (IOException ex) {
            throw new TaskException("创建沙箱工作目录失败：" + ex.getMessage(), TaskException.Code.UNKNOWN, ex);
        }
    }

    private SandboxSessionView toView(SandboxSession session) {
        return new SandboxSessionView(
                session.sessionId,
                session.containerId,
                session.containerName,
                session.imageName,
                session.status,
                baseUrl(session),
                desktopUrl(session),
                session.hostPort,
                session.workspacePath.toString(),
                session.createdAt.toString(),
                session.lastToolResult,
                session.lastScreenshotPath,
                session.lastScreenshotMimeType);
    }

    private String baseUrl(SandboxSession session) {
        return "http://127.0.0.1:" + session.hostPort + "/fastapi";
    }

    private String desktopUrl(SandboxSession session) {
        String password = UriUtils.encodeQueryParam(session.runtimeToken, StandardCharsets.UTF_8);
        return "http://localhost:" + session.hostPort + "/vnc/vnc_lite.html?password=" + password;
    }

    private String randomToken(int bytes) {
        StringBuilder builder = new StringBuilder();
        while (builder.length() < bytes * 2) {
            builder.append(UUID.randomUUID().toString().replace("-", ""));
        }
        return builder.substring(0, bytes * 2);
    }

    private String firstLine(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int index = value.indexOf('\n');
        return (index >= 0 ? value.substring(0, index) : value).trim();
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }

    private void sleepOneSecond() {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public record SandboxStartRequest(String imageName) {}

    public record SandboxNavigateRequest(String url) {}

    public record SandboxToolRequest(String toolName, Map<String, Object> arguments) {}

    public record SandboxSessionView(
            String sessionId,
            String containerId,
            String containerName,
            String imageName,
            String status,
            String baseUrl,
            String desktopUrl,
            Integer hostPort,
            String workspacePath,
            String createdAt,
            String lastToolResult,
            String lastScreenshotPath,
            String lastScreenshotMimeType) {}

    private record CommandResult(int exitCode, String output) {}

    private record ImageData(byte[] bytes, String mimeType) {}

    private record ImagePayload(byte[] bytes, String mimeType, String extension) {}

    private static final class SandboxSession {
        private final String sessionId;
        private final Long userId;
        private final String containerId;
        private final String containerName;
        private final String imageName;
        private final String runtimeToken;
        private final Integer hostPort;
        private final Path workspacePath;
        private final Instant createdAt;
        private volatile String status;
        private volatile String lastToolResult;
        private volatile String lastScreenshotPath;
        private volatile String lastScreenshotMimeType;

        private SandboxSession(
                String sessionId,
                Long userId,
                String containerId,
                String containerName,
                String imageName,
                String runtimeToken,
                Integer hostPort,
                Path workspacePath,
                String status,
                Instant createdAt,
                String lastToolResult,
                String lastScreenshotPath,
                String lastScreenshotMimeType) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.containerId = containerId;
            this.containerName = containerName;
            this.imageName = imageName;
            this.runtimeToken = runtimeToken;
            this.hostPort = hostPort;
            this.workspacePath = workspacePath;
            this.status = status;
            this.createdAt = createdAt;
            this.lastToolResult = lastToolResult;
            this.lastScreenshotPath = lastScreenshotPath;
            this.lastScreenshotMimeType = lastScreenshotMimeType;
        }
    }
}
