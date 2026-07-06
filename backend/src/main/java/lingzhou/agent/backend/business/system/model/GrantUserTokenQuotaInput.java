package lingzhou.agent.backend.business.system.model;

public class GrantUserTokenQuotaInput {

    private Long userId;
    private Long grantTokens;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getGrantTokens() {
        return grantTokens;
    }

    public void setGrantTokens(Long grantTokens) {
        this.grantTokens = grantTokens;
    }
}
