package lingzhou.agent.spring.ai.wechat.ilink.core.retry;

public interface BackoffStrategy {
    long nextDelayMillis(int attempt);
}
