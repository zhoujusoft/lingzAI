package lingzhou.agent.backend.business.system.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

public class TokenQuotaSettingsDto {

    private String configKey;
    private Integer status;
    private Boolean enabled;
    private Long initialGrantTokens;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getInitialGrantTokens() {
        return initialGrantTokens;
    }

    public void setInitialGrantTokens(Long initialGrantTokens) {
        this.initialGrantTokens = initialGrantTokens;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
