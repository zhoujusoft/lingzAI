package lingzhou.agent.backend.business.system.model;

public class UpdateTokenQuotaSettingsInput {

    private Integer status;
    private Long initialGrantTokens;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getInitialGrantTokens() {
        return initialGrantTokens;
    }

    public void setInitialGrantTokens(Long initialGrantTokens) {
        this.initialGrantTokens = initialGrantTokens;
    }
}
