package lingzhou.agent.backend.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag.qa")
public class RagQaProperties {

    private Integer preferenceExpireRounds = 10;
    private Double lowRerankThreshold = 0.35D;
    private String fallbackDisclaimer = "以下回答基于通用模型能力，非知识库依据。";

    public Integer getPreferenceExpireRounds() {
        return preferenceExpireRounds;
    }

    public void setPreferenceExpireRounds(Integer preferenceExpireRounds) {
        this.preferenceExpireRounds = preferenceExpireRounds;
    }

    public Double getLowRerankThreshold() {
        return lowRerankThreshold;
    }

    public void setLowRerankThreshold(Double lowRerankThreshold) {
        this.lowRerankThreshold = lowRerankThreshold;
    }

    public String getFallbackDisclaimer() {
        return fallbackDisclaimer;
    }

    public void setFallbackDisclaimer(String fallbackDisclaimer) {
        this.fallbackDisclaimer = fallbackDisclaimer;
    }
}
