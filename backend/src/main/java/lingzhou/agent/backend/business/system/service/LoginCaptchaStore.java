package lingzhou.agent.backend.business.system.service;

import java.time.Duration;

public interface LoginCaptchaStore {

    void save(String key, String code, Duration ttl);

    String get(String key);

    void delete(String key);
}
