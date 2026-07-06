package lingzhou.agent.backend.business.system.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

public class BrandingSettingsDto {

    private String configKey;

    private Integer status;

    private String systemName;

    private String logoObjectName;

    private String logoUrl;

    private String faviconUrl;

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

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getLogoObjectName() {
        return logoObjectName;
    }

    public void setLogoObjectName(String logoObjectName) {
        this.logoObjectName = logoObjectName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
