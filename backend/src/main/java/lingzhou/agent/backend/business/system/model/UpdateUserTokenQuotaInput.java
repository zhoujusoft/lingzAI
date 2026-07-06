package lingzhou.agent.backend.business.system.model;

public class UpdateUserTokenQuotaInput {

    private Long userId;
    private Long remainingTokens;
    private Boolean unlimited;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRemainingTokens() {
        return remainingTokens;
    }

    public void setRemainingTokens(Long remainingTokens) {
        this.remainingTokens = remainingTokens;
    }

    public Boolean getUnlimited() {
        return unlimited;
    }

    public void setUnlimited(Boolean unlimited) {
        this.unlimited = unlimited;
    }
}
