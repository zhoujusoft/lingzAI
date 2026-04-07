package lingzhou.agent.backend.framework.authentication;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    public void blacklist(String token, Date expiresAt) {
        if (StringUtils.isBlank(token)) {
            return;
        }
        long expireAtMillis =
                expiresAt != null ? expiresAt.getTime() : System.currentTimeMillis() + 24 * 60 * 60 * 1000L;
        blacklist.put(token, expireAtMillis);
    }

    public boolean isBlacklisted(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }
        Long expireAt = blacklist.get(token);
        if (expireAt == null) {
            return false;
        }
        if (expireAt <= System.currentTimeMillis()) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }
}
