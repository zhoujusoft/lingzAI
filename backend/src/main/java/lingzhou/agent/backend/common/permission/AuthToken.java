package lingzhou.agent.backend.common.permission;

import lombok.Data;

@Data
public class AuthToken {

    // jwt token
    private String AccessToken;

    // 用于刷新token的刷新令牌
    private String RefreshToken;

    public AuthToken(String accessToken, String refreshToken) {
        AccessToken = accessToken;
        RefreshToken = refreshToken;
    }

    public AuthToken() {}
}
