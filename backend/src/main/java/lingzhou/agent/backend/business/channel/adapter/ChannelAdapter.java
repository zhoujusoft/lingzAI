package lingzhou.agent.backend.business.channel.adapter;

import lingzhou.agent.backend.business.channel.domain.ChannelConfig;
import lingzhou.agent.backend.business.channel.model.ChannelMessage;

public interface ChannelAdapter {

    void start();

    void stop();

    boolean isRunning();

    void onMessage(ChannelMessage message);

    void sendMessage(String targetId, String content);

    default void sendMessage(Long ownerUserId, String targetId, String content) {
        sendMessage(targetId, content);
    }

    default boolean supportsFileMessage() {
        return false;
    }

    default void sendFileMessage(
            Long ownerUserId, String targetId, byte[] fileBytes, String fileName, String caption) {
        throw new UnsupportedOperationException("current channel does not support file message");
    }

    default void startTyping(Long ownerUserId, String targetId) {
        // Default no-op for channels without typing indicator support.
    }

    default void stopTyping(Long ownerUserId, String targetId) {
        // Default no-op for channels without typing indicator support.
    }

    default boolean supportsProactiveSend() {
        return true;
    }

    default void disconnectUser(Long ownerUserId) {
        // Default no-op for channels without account-scoped runtimes.
    }

    Long getChannelId();

    String getChannelType();

    default String getDisplayName() {
        return getChannelType();
    }

    default void refreshConfig(ChannelConfig channelConfig) {
        // Default no-op for adapters that do not need runtime config refresh.
    }
}
