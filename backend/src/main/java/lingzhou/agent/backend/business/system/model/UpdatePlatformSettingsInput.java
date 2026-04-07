package lingzhou.agent.backend.business.system.model;

import java.util.List;

public class UpdatePlatformSettingsInput {

    private Integer status;

    private List<PlatformEndpointItem> platforms;

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
}
