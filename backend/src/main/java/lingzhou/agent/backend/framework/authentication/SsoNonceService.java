package lingzhou.agent.backend.framework.authentication;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class SsoNonceService {

    private final Map<String, Long> nonceCache = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, long ttlSeconds) {
        if (StringUtils.isBlank(key) || ttlSeconds <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        long expiresAt = now + ttlSeconds * 1000L;
        cleanupExpired(now);
        Long existing = nonceCache.putIfAbsent(key, expiresAt);
        if (existing == null) {
            return true;
        }
        if (existing <= now) {
            nonceCache.put(key, expiresAt);
            return true;
        }
        return false;
    }

    private void cleanupExpired(long now) {
        nonceCache.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
