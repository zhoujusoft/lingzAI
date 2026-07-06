package lingzhou.agent.spring.ai.wechat.ilink.core.listener;

public interface OnHeartbeatListener {
    void onHeartbeatSuccess();

    void onHeartbeatFailure(Throwable cause);
}
