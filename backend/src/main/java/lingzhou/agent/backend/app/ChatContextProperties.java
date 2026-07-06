package lingzhou.agent.backend.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat.context")
public class ChatContextProperties {

    private int maxMessages = 50;
    private int maxHistoryTokens = 32000;
    private int maxUserMessageChars = 2000;
    private int maxAssistantMessageChars = 1200;
    private int maxSystemMessageChars = 400;
    private int maxSummaryMessageChars = 1600;
    private int compressThreshold = 80;
    private int preserveRecentMessages = 30;
    private int minCompressMessages = 12;
    private int summaryCooldownSeconds = 600;
    private int minNewMessagesBeforeResummary = 10;

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getMaxHistoryTokens() {
        return maxHistoryTokens;
    }

    public void setMaxHistoryTokens(int maxHistoryTokens) {
        this.maxHistoryTokens = maxHistoryTokens;
    }

    public int getMaxUserMessageChars() {
        return maxUserMessageChars;
    }

    public void setMaxUserMessageChars(int maxUserMessageChars) {
        this.maxUserMessageChars = maxUserMessageChars;
    }

    public int getMaxAssistantMessageChars() {
        return maxAssistantMessageChars;
    }

    public void setMaxAssistantMessageChars(int maxAssistantMessageChars) {
        this.maxAssistantMessageChars = maxAssistantMessageChars;
    }

    public int getMaxSystemMessageChars() {
        return maxSystemMessageChars;
    }

    public void setMaxSystemMessageChars(int maxSystemMessageChars) {
        this.maxSystemMessageChars = maxSystemMessageChars;
    }

    public int getMaxSummaryMessageChars() {
        return maxSummaryMessageChars;
    }

    public void setMaxSummaryMessageChars(int maxSummaryMessageChars) {
        this.maxSummaryMessageChars = maxSummaryMessageChars;
    }

    public int getCompressThreshold() {
        return compressThreshold;
    }

    public void setCompressThreshold(int compressThreshold) {
        this.compressThreshold = compressThreshold;
    }

    public int getPreserveRecentMessages() {
        return preserveRecentMessages;
    }

    public void setPreserveRecentMessages(int preserveRecentMessages) {
        this.preserveRecentMessages = preserveRecentMessages;
    }

    public int getMinCompressMessages() {
        return minCompressMessages;
    }

    public void setMinCompressMessages(int minCompressMessages) {
        this.minCompressMessages = minCompressMessages;
    }

    public int getSummaryCooldownSeconds() {
        return summaryCooldownSeconds;
    }

    public void setSummaryCooldownSeconds(int summaryCooldownSeconds) {
        this.summaryCooldownSeconds = summaryCooldownSeconds;
    }

    public int getMinNewMessagesBeforeResummary() {
        return minNewMessagesBeforeResummary;
    }

    public void setMinNewMessagesBeforeResummary(int minNewMessagesBeforeResummary) {
        this.minNewMessagesBeforeResummary = minNewMessagesBeforeResummary;
    }
}
