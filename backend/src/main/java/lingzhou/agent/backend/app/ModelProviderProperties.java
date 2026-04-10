package lingzhou.agent.backend.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "model")
public class ModelProviderProperties {

    private VendorProperties qwen = new VendorProperties();
    private VendorProperties vllm = new VendorProperties();

    public VendorProperties getQwen() {
        return qwen;
    }

    public void setQwen(VendorProperties qwen) {
        this.qwen = qwen;
    }

    public VendorProperties getVllm() {
        return vllm;
    }

    public void setVllm(VendorProperties vllm) {
        this.vllm = vllm;
    }

    public VendorProperties resolve(String adapterType) {
        if ("VLLM".equalsIgnoreCase(adapterType) || "vllm".equalsIgnoreCase(adapterType)) {
            return vllm;
        }
        return qwen;
    }

    public static class VendorProperties {

        private ChatProperties chat = new ChatProperties();
        private EmbeddingProperties embedding = new EmbeddingProperties();
        private RerankProperties rerank = new RerankProperties();

        public ChatProperties getChat() {
            return chat;
        }

        public void setChat(ChatProperties chat) {
            this.chat = chat;
        }

        public EmbeddingProperties getEmbedding() {
            return embedding;
        }

        public void setEmbedding(EmbeddingProperties embedding) {
            this.embedding = embedding;
        }

        public RerankProperties getRerank() {
            return rerank;
        }

        public void setRerank(RerankProperties rerank) {
            this.rerank = rerank;
        }
    }

    public static class ChatProperties {

        private Double temperature;
        private Integer maxTokens;
        private String systemPrompt;
        private Boolean enableThinking = Boolean.FALSE;

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public Boolean getEnableThinking() {
            return enableThinking;
        }

        public void setEnableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
        }
    }

    public static class EmbeddingProperties {

        private Integer dimensions;

        public Integer getDimensions() {
            return dimensions;
        }

        public void setDimensions(Integer dimensions) {
            this.dimensions = dimensions;
        }
    }

    public static class RerankProperties {

        private String protocol;
        private Integer timeoutMs = 1200;
        private Boolean fallbackRrf = Boolean.TRUE;

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public Integer getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(Integer timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public Boolean getFallbackRrf() {
            return fallbackRrf;
        }

        public void setFallbackRrf(Boolean fallbackRrf) {
            this.fallbackRrf = fallbackRrf;
        }
    }
}
