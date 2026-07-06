package lingzhou.agent.backend.business.system.model;

public class BrandingLogoUploadResult {

    private String logoObjectName;

    private String logoUrl;

    private String faviconUrl;

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
}
