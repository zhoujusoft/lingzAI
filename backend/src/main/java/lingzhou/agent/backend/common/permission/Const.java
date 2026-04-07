package lingzhou.agent.backend.common.permission;

public class Const {

    private Const() {}

    public static final String RefreshTokenIdClaimType = "refresh_token_id";
    public static final String DeviceTypeClaimType = "device_type";
    public static final String TokenTypeClaimType = "token_type";
    public static final String RememberMeClaimType = "remember_me";
    public static final String AccessTokenType = "access";
    public static final String RefreshTokenType = "refresh";
    public static final String TokenHashCacheKey = "Auth_Token";
    public static final String UserAccountClaimType = "user_account";
    public static final String SsoClaimType = "sub";
    public static final String TnCode = "TnCode";
    public static final String AuthorizationHeader = "Authorization";
    public static final String XAccessToken = "X-ACCESS-TOKEN";
    public static final String XRefreshToken = "X-REFRESH-TOKEN";

    public static final long AccessTokenExpireSeconds = 2 * 60 * 60L;
    public static final long RefreshTokenExpireSecondsRememberMe = 7 * 24 * 60 * 60L;
    public static final long RefreshTokenExpireSecondsDefault = 24 * 60 * 60L;
}
