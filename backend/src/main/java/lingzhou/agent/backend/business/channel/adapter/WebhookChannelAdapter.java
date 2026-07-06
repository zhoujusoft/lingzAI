package lingzhou.agent.backend.business.channel.adapter;

import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.service.ChannelMessageRouter;

public class WebhookChannelAdapter extends AbstractChannelAdapter {

    public WebhookChannelAdapter(ChannelConfig channelConfig, ChannelMessageRouter messageRouter) {
        super(channelConfig, messageRouter);
    }

    @Override
    public String getChannelType() {
        return channelConfig.getChannelType();
    }

    @Override
    public void sendMessage(String targetId, String content) {
        logger.info("被动渠道发送消息降级为日志：channelId={}, targetId={}, content={}", channelConfig.getId(), targetId, content);
    }

    @Override
    protected void doStart() {
        logger.info("被动渠道已启动：channelId={}, channelType={}", channelConfig.getId(), channelConfig.getChannelType());
    }

    @Override
    protected void doStop() {
        logger.info("被动渠道已停止：channelId={}", channelConfig.getId());
    }
}
