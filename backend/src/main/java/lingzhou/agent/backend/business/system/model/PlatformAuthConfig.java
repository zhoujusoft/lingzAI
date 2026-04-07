package lingzhou.agent.backend.business.system.model;

public class PlatformAuthConfig {

    private String username;

    private String password;

    private String tncode;

    private String userId;

    private Boolean credentialConfigured;

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
}
