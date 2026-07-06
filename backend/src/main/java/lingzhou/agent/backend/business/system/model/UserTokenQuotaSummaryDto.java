package lingzhou.agent.backend.business.system.model;

public class UserTokenQuotaSummaryDto {

    private Boolean enabled;
    private Boolean unlimited;
    private Long grantedTokens;
    private Long consumedTokens;
    private Long remainingTokens;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getUnlimited() {
        return unlimited;
    }

    public void setUnlimited(Boolean unlimited) {
        this.unlimited = unlimited;
    }

    public Long getGrantedTokens() {
        return grantedTokens;
    }

    public void setGrantedTokens(Long grantedTokens) {
        this.grantedTokens = grantedTokens;
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
}
