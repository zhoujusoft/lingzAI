package lingzhou.agent.backend.business.license.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.List;

public class LicenseStatusView {

    private boolean enabled;
    private String status;
    private String productCode;
    private String customerName;
    private String edition;
    private LicenseType licType;
    private String instanceCode;
    private String licenseId;
    private Integer revision;
    private Integer activeUsers;
    private Integer maxActiveUsers;
    private Long consumedTokens;
    private Long remainingTokens;
    private Long maxTotalTokens;
    private List<String> featureFlags;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date effectiveAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date importedAt;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public LicenseType getLicType() {
        return licType;
    }

    public void setLicType(LicenseType licType) {
        this.licType = licType;
    }

    public String getInstanceCode() {
        return instanceCode;
    }

    public void setInstanceCode(String instanceCode) {
        this.instanceCode = instanceCode;
    }

    public String getLicenseId() {
        return licenseId;
    }

    public void setLicenseId(String licenseId) {
        this.licenseId = licenseId;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public Integer getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Integer activeUsers) {
        this.activeUsers = activeUsers;
    }

    public Integer getMaxActiveUsers() {
        return maxActiveUsers;
    }

    public void setMaxActiveUsers(Integer maxActiveUsers) {
        this.maxActiveUsers = maxActiveUsers;
    }

    public Long getConsumedTokens() {
        return consumedTokens;
    }

    public void setConsumedTokens(Long consumedTokens) {
        this.consumedTokens = consumedTokens;
    }

    public Long getRemainingTokens() {
        return remainingTokens;
    }

    public void setRemainingTokens(Long remainingTokens) {
        this.remainingTokens = remainingTokens;
    }

    public Long getMaxTotalTokens() {
        return maxTotalTokens;
    }

    public void setMaxTotalTokens(Long maxTotalTokens) {
        this.maxTotalTokens = maxTotalTokens;
    }

    public List<String> getFeatureFlags() {
        return featureFlags;
    }

    public void setFeatureFlags(List<String> featureFlags) {
        this.featureFlags = featureFlags;
    }

    public Date getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(Date effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Date getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Date importedAt) {
        this.importedAt = importedAt;
    }
}
