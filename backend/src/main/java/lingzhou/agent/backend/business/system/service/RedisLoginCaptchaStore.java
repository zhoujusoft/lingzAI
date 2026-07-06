package lingzhou.agent.backend.business.system.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisLoginCaptchaStore implements LoginCaptchaStore {

    private static final String CAPTCHA_KEY_PREFIX = "login:captcha:";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void save(String key, String code, Duration ttl) {
        redisTemplate.opsForValue().set(buildCacheKey(key), code, ttl);
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(buildCacheKey(key));
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(buildCacheKey(key));
    }

    private String buildCacheKey(String key) {
        return CAPTCHA_KEY_PREFIX + key;
    }
}
