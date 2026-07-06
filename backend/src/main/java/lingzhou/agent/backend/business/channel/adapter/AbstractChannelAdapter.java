package lingzhou.agent.backend.business.channel.adapter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.model.ChannelMessage;
import lingzhou.agent.backend.business.channel.service.ChannelMessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public abstract class AbstractChannelAdapter implements ChannelAdapter {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected volatile ChannelConfig channelConfig;
    protected final ChannelMessageRouter messageRouter;
    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected volatile Map<String, Object> config;

    protected AbstractChannelAdapter(ChannelConfig channelConfig, ChannelMessageRouter messageRouter) {
        this.channelConfig = channelConfig;
        this.messageRouter = messageRouter;
        this.config = parseConfig(channelConfig.getConfigJson());
    }

    @Override
    public void refreshConfig(ChannelConfig channelConfig) {
        if (channelConfig == null) {
            return;
        }
        this.channelConfig = channelConfig;
        this.config = parseConfig(channelConfig.getConfigJson());
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            doStart();
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            doStop();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void onMessage(ChannelMessage message) {
        if (message == null) {
            return;
        }
        if (!shouldProcess(message)) {
            return;
        }
        if (StringUtils.hasText(channelConfig.getBotPrefix()) && StringUtils.hasText(message.getContent())) {
            message.setContent(cleanPrefix(message.getContent(), channelConfig.getBotPrefix()));
        }
        messageRouter.enqueue(message, this, channelConfig);
    }

    @Override
    public Long getChannelId() {
        return channelConfig.getId();
    }

    protected Map<String, Object> parseConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return Collections.emptyMap();
        }
        try {
            return JSON.parseObject(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            logger.warn("解析渠道配置失败：channelId={}, error={}", channelConfig.getId(), ex.getMessage());
            return Collections.emptyMap();
        }
    }

    protected boolean shouldProcess(ChannelMessage message) {
        if (!StringUtils.hasText(channelConfig.getBotPrefix())) {
            return true;
        }
        return StringUtils.hasText(message.getContent())
                && message.getContent()
                        .trim()
                        .startsWith(channelConfig.getBotPrefix().trim());
    }

    protected String cleanPrefix(String content, String prefix) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(prefix)) {
            return content;
        }
        String trimmedContent = content.trim();
        String trimmedPrefix = prefix.trim();
        if (!trimmedContent.startsWith(trimmedPrefix)) {
            return content;
        }
        String cleaned = trimmedContent.substring(trimmedPrefix.length()).trim();
        return StringUtils.hasText(cleaned) ? cleaned : trimmedContent;
    }

    protected String getString(String key, String defaultValue) {
        Object value = config.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    protected boolean getBoolean(String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    protected long getLong(String key, long defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    protected int getInt(String key, int defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    protected abstract void doStart();

    protected abstract void doStop();
}
