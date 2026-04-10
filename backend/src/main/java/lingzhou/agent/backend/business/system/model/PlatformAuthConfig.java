package lingzhou.agent.backend.business.system.model;

public class PlatformAuthConfig {

    private String username;

    private String password;

    private String appKey;

    private String appSecret;

    private String rsaPublicKey;

    private String tncode;

    private String userId;

    private Boolean credentialConfigured;

    private Boolean signatureConfigured;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getRsaPublicKey() {
        return rsaPublicKey;
    }

    public void setRsaPublicKey(String rsaPublicKey) {
        this.rsaPublicKey = rsaPublicKey;
    }

    public String getTncode() {
        return tncode;
    }

    public void setTncode(String tncode) {
        this.tncode = tncode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Boolean getCredentialConfigured() {
        return credentialConfigured;
    }

    public void setCredentialConfigured(Boolean credentialConfigured) {
        this.credentialConfigured = credentialConfigured;
    }

    public Boolean getSignatureConfigured() {
        return signatureConfigured;
    }

    public void setSignatureConfigured(Boolean signatureConfigured) {
        this.signatureConfigured = signatureConfigured;
    }
}
