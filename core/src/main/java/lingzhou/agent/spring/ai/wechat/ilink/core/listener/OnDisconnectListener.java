package lingzhou.agent.spring.ai.wechat.ilink.core.listener;

public interface OnDisconnectListener {
    void onDisconnect(Throwable cause);

    void onReconnectStart(int attempt);

    void onReconnectSuccess();

    void onReconnectFailed(Throwable cause);
}
