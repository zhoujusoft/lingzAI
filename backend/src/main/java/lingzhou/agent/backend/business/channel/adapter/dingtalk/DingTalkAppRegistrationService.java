package lingzhou.agent.backend.business.channel.adapter.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DingTalkAppRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(DingTalkAppRegistrationService.class);
    private static final String API_BASE = "https://oapi.dingtalk.com";
    private static final String SOURCE = "LINGZHOU_AGENT";
    private static final long POLL_INTERVAL_MS = 5_000L;
    private static final long INIT_REQUEST_TIMEOUT_MS = 15_000L;
    private static final long POLL_REQUEST_TIMEOUT_MS = 10_000L;
    private static final long SESSION_TTL_MS = 7 * 60_000L;
    private static final long WORKER_MAX_RUNTIME_MS = 6 * 60_000L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ConcurrentHashMap<String, RegistrationSession> sessions = new ConcurrentHashMap<>();

    public RegistrationSession begin() throws Exception {
        evictExpiredSessions();
        Map<?, ?> init = postJson("/app/registration/init", Map.of("source", SOURCE), INIT_REQUEST_TIMEOUT_MS);
        Integer initCode = asInteger(init.get("errcode"));
        if (initCode != null && initCode != 0) {
            throw new IllegalStateException("钉钉应用注册初始化失败: errcode=" + initCode + ", errmsg=" + init.get("errmsg"));
        }
        String nonce = asString(init.get("nonce"));
        if (!StringUtils.hasText(nonce)) {
            throw new IllegalStateException("钉钉应用注册初始化失败: 缺少 nonce");
        }

        Map<?, ?> begin = postJson("/app/registration/begin", Map.of("nonce", nonce), INIT_REQUEST_TIMEOUT_MS);
        Integer beginCode = asInteger(begin.get("errcode"));
        if (beginCode != null && beginCode != 0) {
            throw new IllegalStateException("钉钉应用注册启动失败: errcode=" + beginCode + ", errmsg=" + begin.get("errmsg"));
        }
        String deviceCode = asString(begin.get("device_code"));
        String verificationUri = asString(begin.get("verification_uri_complete"));
        if (!StringUtils.hasText(deviceCode) || !StringUtils.hasText(verificationUri)) {
            throw new IllegalStateException("钉钉应用注册启动失败: 缺少 device_code 或二维码地址");
        }

        String sessionId = UUID.randomUUID().toString();
        RegistrationSession session = new RegistrationSession(sessionId);
        session.qrcodeUrl = verificationUri;
        session.status = Status.WAITING;
        sessions.put(sessionId, session);

        Thread worker = new Thread(
                () -> pollUntilTerminal(session, deviceCode), "dingtalk-register-" + sessionId.substring(0, 8));
        worker.setDaemon(true);
        worker.start();
        logger.info(
                "钉钉应用扫码注册已启动：sessionId={}, deviceCodeSuffix={}",
                sessionId,
                suffix(deviceCode));
        return session;
    }

    public RegistrationSession getSession(String sessionId) {
        evictExpiredSessions();
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        return sessions.get(sessionId);
    }

    private void pollUntilTerminal(RegistrationSession session, String deviceCode) {
        long startedAt = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - startedAt > WORKER_MAX_RUNTIME_MS) {
                session.status = Status.EXPIRED;
                session.errorMessage = "扫码注册轮询超时";
                session.lastUpdateMs = System.currentTimeMillis();
                logger.warn("钉钉应用扫码注册超时：sessionId={}", session.sessionId);
                return;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                Map<?, ?> poll =
                        postJson("/app/registration/poll", Map.of("device_code", deviceCode), POLL_REQUEST_TIMEOUT_MS);
                session.lastUpdateMs = System.currentTimeMillis();
                String status = asString(poll.get("status"));
                if (!StringUtils.hasText(status)) {
                    status = "WAITING";
                }
                switch (status) {
                    case "SUCCESS" -> {
                        session.clientId = asString(poll.get("client_id"));
                        session.clientSecret = asString(poll.get("client_secret"));
                        session.status = Status.CONFIRMED;
                        logger.info("钉钉应用扫码注册完成：sessionId={}, clientId={}", session.sessionId, session.clientId);
                        return;
                    }
                    case "FAIL" -> {
                        session.errorMessage = asString(poll.get("fail_reason"));
                        session.status = Status.DENIED;
                        logger.info("钉钉应用扫码注册被拒绝：sessionId={}, reason={}", session.sessionId, session.errorMessage);
                        return;
                    }
                    case "EXPIRED" -> {
                        session.status = Status.EXPIRED;
                        logger.info("钉钉应用扫码注册已过期：sessionId={}", session.sessionId);
                        return;
                    }
                    case "WAITING" -> {
                        // keep polling
                    }
                    default -> logger.debug("钉钉应用扫码注册返回未知状态：sessionId={}, status={}", session.sessionId, status);
                }
            } catch (Exception ex) {
                logger.debug("钉钉应用扫码注册轮询失败，将继续重试：sessionId={}, error={}", session.sessionId, ex.getMessage());
            }
        }
    }

    private Map<?, ?> postJson(String path, Map<String, ?> body, long timeoutMs) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + path))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("钉钉接口 " + path + " 返回 HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), Map.class);
    }

    private void evictExpiredSessions() {
        long cutoff = System.currentTimeMillis() - SESSION_TTL_MS;
        Iterator<Map.Entry<String, RegistrationSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().createdAtMs < cutoff) {
                iterator.remove();
            }
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String suffix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > 6 ? "..." + value.substring(value.length() - 6) : value;
    }

    public enum Status {
        WAITING,
        CONFIRMED,
        EXPIRED,
        DENIED
    }

    public static class RegistrationSession {

        public final String sessionId;
        private final long createdAtMs = System.currentTimeMillis();
        public volatile Status status = Status.WAITING;
        public volatile String qrcodeUrl;
        public volatile String qrcodeImageDataUri;
        public volatile String clientId;
        public volatile String clientSecret;
        public volatile String errorMessage;
        public volatile long lastUpdateMs = System.currentTimeMillis();

        RegistrationSession(String sessionId) {
            this.sessionId = sessionId;
        }
    }
}
