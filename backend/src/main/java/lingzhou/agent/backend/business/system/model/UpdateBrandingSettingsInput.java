package lingzhou.agent.backend.business.system.model;

public class UpdateBrandingSettingsInput {

    private String systemName;

    private String logoObjectName;

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
}
