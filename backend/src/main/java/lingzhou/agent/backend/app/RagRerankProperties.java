package lingzhou.agent.backend.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag.rerank")
public class RagRerankProperties {

    private Boolean enabled = Boolean.TRUE;
    private String baseUrl;
    private String apiKey;
    private String path = "/api/v1/services/rerank/text-rerank/text-rerank";
    private String model = "gte-rerank-v2";
    private String protocol;
    private Integer timeoutMs = 1200;
    private Boolean fallbackRrf = Boolean.TRUE;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

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
