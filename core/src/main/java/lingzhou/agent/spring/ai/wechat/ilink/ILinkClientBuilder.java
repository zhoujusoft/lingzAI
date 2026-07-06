package lingzhou.agent.spring.ai.wechat.ilink;

import lingzhou.agent.spring.ai.wechat.ilink.core.config.ConfigLoader;
import lingzhou.agent.spring.ai.wechat.ilink.core.config.ILinkConfig;
import lingzhou.agent.spring.ai.wechat.ilink.core.listener.*;

public class ILinkClientBuilder {
    private ILinkConfig config = ConfigLoader.loadDefault();
    private final ListenerRegistry listenerRegistry = new ListenerRegistry();

    public ILinkClientBuilder config(ILinkConfig config) {
        this.config = config;
        return this;
    }

    public ILinkClientBuilder onLogin(OnLoginListener l) {
        listenerRegistry.addOnLoginListener(l);
        return this;
    }

    public ILinkClientBuilder onDisconnect(OnDisconnectListener l) {
        listenerRegistry.addOnDisconnectListener(l);
        return this;
    }

    public ILinkClientBuilder onHeartbeat(OnHeartbeatListener l) {
        listenerRegistry.addOnHeartbeatListener(l);
        return this;
    }

    public ILinkClientBuilder onMessage(OnMessageListener l) {
        listenerRegistry.addOnMessageListener(l);
        return this;
    }

    public ILinkClient build() {
        return new ILinkClient(config, listenerRegistry);
    }
}
