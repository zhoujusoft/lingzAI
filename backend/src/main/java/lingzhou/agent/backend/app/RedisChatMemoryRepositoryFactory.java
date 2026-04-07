package lingzhou.agent.backend.app;

import org.springframework.ai.chat.memory.ChatMemoryRepository;

/**
 * Extension hook for Redis-backed memory.
 * <p>Provide a bean implementing this interface to plug in Redis storage.
 */
public interface RedisChatMemoryRepositoryFactory {

    ChatMemoryRepository create();
}
