package lingzhou.agent.backend.business.skill.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lingzhou.agent.backend.capability.api.client.LowcodePlatformClient;
import lingzhou.agent.backend.business.system.model.PlatformEndpointItem;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LowcodeTokenService {

    private static final Logger logger = LoggerFactory.getLogger(LowcodeTokenService.class);
    private static final long DEFAULT_TTL_SECONDS = 20 * 60;
    private static final long EARLY_REFRESH_SECONDS = 60;

    private final Map<String, TokenHolder> tokenCache = new ConcurrentHashMap<>();
    private final LowcodePlatformClient lowcodePlatformClient;
    private final ObjectMapper objectMapper;

    public LowcodeTokenService(LowcodePlatformClient lowcodePlatformClient, ObjectMapper objectMapper) {
        this.lowcodePlatformClient = lowcodePlatformClient;
        this.objectMapper = objectMapper;
    }

    public String getToken(PlatformEndpointItem platform) throws TaskException {
        String platformKey = platform.getKey();
        TokenHolder holder = tokenCache.get(platformKey);
        if (holder != null && !holder.expired()) {
            return holder.token();
        }
        synchronized (platformKey.intern()) {
            holder = tokenCache.get(platformKey);
            if (holder != null && !holder.expired()) {
                return holder.token();
            }
            String token = login(platform);
            long expiresAt = resolveExpiryEpochSeconds(token);
            tokenCache.put(platformKey, new TokenHolder(token, expiresAt));
            return token;
        }
    }

    public String getTokenIfConfigured(PlatformEndpointItem platform) throws TaskException {
        return getToken(platform);
    }

    public void invalidate(String platformKey) {
        if (StringUtils.hasText(platformKey)) {
            tokenCache.remove(platformKey.trim());
        }
    }

    private String login(PlatformEndpointItem platform) throws TaskException {
        try {
            LowcodePlatformClient.PlatformEnvelope envelope = lowcodePlatformClient.getToken(platform);
            Object result = envelope.result();
            if (result instanceof String resultText && StringUtils.hasText(resultText)) {
                return resultText.trim();
            }
            if (result instanceof Map<?, ?> map) {
                Object tokenValue = map.get("Token");
                if (tokenValue == null) {
                    tokenValue = map.get("access_token");
                }
                if (tokenValue == null) {
                    tokenValue = map.get("AccessToken");
                }
                if (tokenValue != null && StringUtils.hasText(String.valueOf(tokenValue))) {
                    return String.valueOf(tokenValue).trim();
                }
            }
            String serialized = objectMapper.writeValueAsString(result);
            Map<String, Object> parsed = objectMapper.readValue(serialized, new TypeReference<Map<String, Object>>() {});
            Object tokenValue = parsed.get("token");
            if (tokenValue == null) {
                tokenValue = parsed.get("access_token");
            }
            if (tokenValue == null) {
                tokenValue = parsed.get("AccessToken");
            }
            if (tokenValue != null && StringUtils.hasText(String.valueOf(tokenValue))) {
                return String.valueOf(tokenValue).trim();
            }
            throw new TaskException("平台登录失败：未返回 token", TaskException.Code.UNKNOWN);
        } catch (TaskException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.warn("低代码平台登录失败：platformKey={}, error={}", platform.getKey(), ex.getMessage(), ex);
            throw new TaskException("平台登录失败", TaskException.Code.UNKNOWN, ex);
        }
    }

    private long resolveExpiryEpochSeconds(String token) {
        long fallback = Instant.now().getEpochSecond() + DEFAULT_TTL_SECONDS;
        if (!StringUtils.hasText(token)) {
            return fallback;
        }
        try {
            Claims claims = Jwts.parser().parseClaimsJwt(extractUnsignedJwt(token)).getBody();
            if (claims.getExpiration() != null) {
                return Math.max(Instant.now().getEpochSecond() + 120, claims.getExpiration().toInstant().getEpochSecond() - EARLY_REFRESH_SECONDS);
            }
        } catch (Exception ignored) {
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                Map<String, Object> payloadMap = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
                Object exp = payloadMap.get("exp");
                if (exp instanceof Number number) {
                    return Math.max(Instant.now().getEpochSecond() + 120, number.longValue() - EARLY_REFRESH_SECONDS);
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private String extractUnsignedJwt(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return token;
        }
        return parts[0] + "." + parts[1] + ".";
    }

    private record TokenHolder(String token, long expiresAtEpochSeconds) {
        private boolean expired() {
            return !StringUtils.hasText(token) || Instant.now().getEpochSecond() >= expiresAtEpochSeconds;
        }
    }
}
