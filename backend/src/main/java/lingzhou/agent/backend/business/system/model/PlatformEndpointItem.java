package lingzhou.agent.backend.business.system.model;

public class PlatformEndpointItem {

    private String key;

    private String name;

    private String apiUrl;

    private Integer status;

    private String authType;

    private PlatformAuthConfig authConfig;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public PlatformAuthConfig getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(PlatformAuthConfig authConfig) {
        this.authConfig = authConfig;
    }
}
