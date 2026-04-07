package lingzhou.agent.backend.common.login;

import lingzhou.agent.backend.common.enums.LoginInfoState;
import lombok.Data;

@Data
public class DomainLoginDto {
    private LoginInfoState State;

    private String AccessToken;

    private String RefreshToken;

    //    @JsonProperty("SubDomain")
    //    private String SubDomain;

    private String Message;

    private String ExUserId;

    private String ExDingTalkId;
}
