package lingzhou.agent.backend.business.system.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.List;

public class PlatformSettingsDto {

    private String configKey;

    private Integer status;

    private List<PlatformEndpointItem> platforms;

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

    public List<PlatformEndpointItem> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(List<PlatformEndpointItem> platforms) {
        this.platforms = platforms;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
