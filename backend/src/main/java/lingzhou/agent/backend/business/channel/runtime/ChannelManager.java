package lingzhou.agent.backend.business.channel.runtime;

import jakarta.annotation.PreDestroy;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lingzhou.agent.backend.business.channel.adapter.ChannelAdapter;
import lingzhou.agent.backend.business.channel.adapter.WebhookChannelAdapter;
import lingzhou.agent.backend.business.channel.adapter.dingtalk.DingTalkChannelAdapter;
import lingzhou.agent.backend.business.channel.adapter.wechat.WechatIlinkChannelAdapter;
import lingzhou.agent.backend.business.channel.adapter.wecom.WeComChannelAdapter;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.service.ChannelConfigService;
import lingzhou.agent.backend.business.channel.service.ChannelMessageRouter;
import lingzhou.agent.backend.business.channel.service.ChannelSessionBindingService;
import lingzhou.agent.backend.business.channel.service.ChannelUserBindingService;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ChannelManager {

    private static final Logger logger = LoggerFactory.getLogger(ChannelManager.class);

    private final ChannelConfigService channelConfigService;
    private final ChannelMessageRouter channelMessageRouter;
    private final ChannelSessionBindingService channelSessionBindingService;
    private final ChannelUserBindingService channelUserBindingService;
    private final ChatFileService chatFileService;
    private final Map<Long, ChannelAdapter> activeAdapters = new ConcurrentHashMap<>();
    private final Object adapterLifecycleMonitor = new Object();

    public ChannelManager(
            ChannelConfigService channelConfigService,
            ChannelMessageRouter channelMessageRouter,
            ChannelSessionBindingService channelSessionBindingService,
            ChannelUserBindingService channelUserBindingService,
            ChatFileService chatFileService) {
        this.channelConfigService = channelConfigService;
        this.channelMessageRouter = channelMessageRouter;
        this.channelSessionBindingService = channelSessionBindingService;
        this.channelUserBindingService = channelUserBindingService;
        this.chatFileService = chatFileService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        for (ChannelConfig config : channelConfigService.listEnabled()) {
            try {
                startChannel(config);
            } catch (Exception ex) {
                logger.error("启动渠道失败：channelId={}, error={}", config.getId(), ex.getMessage(), ex);
            }
        }
    }

    public Collection<ChannelAdapter> getActiveAdapters() {
        return activeAdapters.values();
    }

    public Optional<ChannelAdapter> getAdapter(Long channelId) {
        return Optional.ofNullable(activeAdapters.get(channelId));
    }

    public ChannelAdapter getOrStartAdapter(Long channelId) {
        ChannelAdapter existing = activeAdapters.get(channelId);
        if (existing != null) {
            return existing;
        }
        return startChannel(channelId);
    }

    public ChannelAdapter startChannel(Long channelId) {
        return startChannel(channelConfigService.getRequired(channelId));
    }

    public ChannelAdapter startChannel(ChannelConfig config) {
        synchronized (adapterLifecycleMonitor) {
            return startChannelLocked(config);
        }
    }

    public void stopChannel(Long channelId) {
        synchronized (adapterLifecycleMonitor) {
            ChannelAdapter adapter = activeAdapters.remove(channelId);
            if (adapter != null) {
                adapter.stop();
            }
        }
    }

    public ChannelAdapter restartChannel(Long channelId) {
        synchronized (adapterLifecycleMonitor) {
            ChannelAdapter adapter = activeAdapters.remove(channelId);
            if (adapter != null) {
                adapter.stop();
            }
            return startChannelLocked(channelConfigService.getRequired(channelId));
        }
    }

    public void syncChannel(ChannelConfig config) {
        synchronized (adapterLifecycleMonitor) {
            if (Boolean.TRUE.equals(config.getEnabled())) {
                ChannelAdapter existing = activeAdapters.get(config.getId());
                if (existing != null) {
                    existing.refreshConfig(config);
                    if ("wecom".equalsIgnoreCase(config.getChannelType())
                            || "dingtalk".equalsIgnoreCase(config.getChannelType())) {
                        // Long-lived IM subscriptions must reconnect after credential or bot config changes.
                        existing.stop();
                        existing.start();
                        logger.info(
                                "渠道配置已更新并重连：channelId={}, channelType={}",
                                config.getId(),
                                config.getChannelType());
                        return;
                    }
                    if (!existing.isRunning()) {
                        existing.start();
                    }
                    logger.info("渠道配置已热更新：channelId={}, channelType={}", config.getId(), config.getChannelType());
                    return;
                }
                startChannelLocked(config);
                return;
            }
            ChannelAdapter adapter = activeAdapters.remove(config.getId());
            if (adapter != null) {
                adapter.stop();
            }
        }
    }

    public void sendMessage(Long channelId, String targetId, String content) {
        sendMessage(channelId, null, targetId, content);
    }

    public void sendMessage(Long channelId, Long ownerUserId, String targetId, String content) {
        ChannelAdapter adapter = getOrStartAdapter(channelId);
        adapter.sendMessage(ownerUserId, targetId, content);
    }

    public void disconnectUser(Long channelId, Long ownerUserId) {
        ChannelAdapter adapter = activeAdapters.get(channelId);
        if (adapter != null) {
            adapter.disconnectUser(ownerUserId);
        }
    }

    @PreDestroy
    public void destroy() {
        synchronized (adapterLifecycleMonitor) {
            for (ChannelAdapter adapter : activeAdapters.values()) {
                try {
                    adapter.stop();
                } catch (Exception ex) {
                    logger.warn("停止渠道失败：channelId={}, error={}", adapter.getChannelId(), ex.getMessage());
                }
            }
            activeAdapters.clear();
        }
    }

    private ChannelAdapter createAdapter(ChannelConfig config) {
        if ("wecom".equalsIgnoreCase(config.getChannelType())) {
            return new WeComChannelAdapter(config, channelMessageRouter, channelUserBindingService, chatFileService);
        }
        if ("weixin".equalsIgnoreCase(config.getChannelType())) {
            return new WechatIlinkChannelAdapter(
                    config,
                    channelMessageRouter,
                    channelSessionBindingService,
                    channelUserBindingService,
                    chatFileService);
        }
        if ("dingtalk".equalsIgnoreCase(config.getChannelType())) {
            return new DingTalkChannelAdapter(config, channelMessageRouter, chatFileService, channelUserBindingService);
        }
        return new WebhookChannelAdapter(config, channelMessageRouter);
    }

    private ChannelAdapter startChannelLocked(ChannelConfig config) {
        ChannelAdapter existing = activeAdapters.get(config.getId());
        if (existing != null) {
            return existing;
        }
        ChannelAdapter adapter = createAdapter(config);
        adapter.start();
        activeAdapters.put(config.getId(), adapter);
        logger.info(
                "渠道已启动：channelId={}, channelType={}, adapterType={}, supportsFileMessage={}",
                config.getId(),
                config.getChannelType(),
                adapter.getClass().getName(),
                adapter.supportsFileMessage());
        return adapter;
    }
}
