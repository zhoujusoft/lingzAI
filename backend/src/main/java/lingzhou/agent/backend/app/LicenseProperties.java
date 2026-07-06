package lingzhou.agent.backend.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.license")
public class LicenseProperties {

    private boolean enabled = true;

    private String productCode = "LINGZHOU-AGENT";

    private String instanceCode = "";

    private int expiringSoonDays = 15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getInstanceCode() {
        return instanceCode;
    }

    public void setInstanceCode(String instanceCode) {
        this.instanceCode = instanceCode;
    }

    public int getExpiringSoonDays() {
        return expiringSoonDays;
    }

    public void setExpiringSoonDays(int expiringSoonDays) {
        this.expiringSoonDays = expiringSoonDays;
    }
}
