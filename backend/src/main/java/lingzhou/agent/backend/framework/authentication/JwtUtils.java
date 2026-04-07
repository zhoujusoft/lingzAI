/**
 * Copyright (c) 2016-2019 人人开源 All rights reserved.
 * <p>
 * https://www.renren.io
 * <p>
 * 版权所有，侵权必究！
 */
package lingzhou.agent.backend.framework.authentication;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.DefaultClaims;
import java.util.Date;
import lingzhou.agent.backend.common.config.Config;
import lingzhou.agent.backend.common.enums.DeviceType;
import lingzhou.agent.backend.common.lzException.ExceptionCode;
import lingzhou.agent.backend.common.lzException.LZException;
import lingzhou.agent.backend.common.permission.AuthToken;
import lingzhou.agent.backend.common.permission.Const;
import lingzhou.agent.backend.common.utils.GuidUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * jwt工具类
 *
 * @author Mark sunlightcs@gmail.com
 */
@Component
public class JwtUtils {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private String secret = "f4e2e52034348f86b67cde581c0f9eb5";
    private long expire = Const.AccessTokenExpireSeconds;
    private String header = Const.AuthorizationHeader;

    /**
     * 生成 access token（兼容旧调用）
     */
    public String generateToken(String userId, String code) {
        return generateToken(userId, code, getExpire(), Const.AccessTokenType, null, false);
    }

    public AuthToken createTokenPair(String userId, String code, boolean rememberMe) throws Exception {
        if (StringUtils.isBlank(code) || StringUtils.isBlank(userId)) {
            throw new RuntimeException();
        }

        long refreshExpireSeconds =
                rememberMe ? Const.RefreshTokenExpireSecondsRememberMe : Const.RefreshTokenExpireSecondsDefault;
        String refreshTokenId = GuidUtil.ToShort();
        String accessToken = generateToken(
                userId, code, Const.AccessTokenExpireSeconds, Const.AccessTokenType, refreshTokenId, rememberMe);
        String refreshToken =
                generateToken(userId, code, refreshExpireSeconds, Const.RefreshTokenType, refreshTokenId, rememberMe);
        return new AuthToken(accessToken, refreshToken);
    }

    public String getMacAddressByToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.get("MacAddress").toString();
        } catch (Exception e) {
            logger.debug("validate is token error ", e);
            return null;
        }
    }

    public String getUserIdByToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.get("UserId").toString();
        } catch (Exception e) {
            logger.debug("validate is token error ", e);
            return null;
        }
    }

    public String getCodeByToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.get(Const.UserAccountClaimType).toString();
        } catch (Exception e) {
            logger.debug("validate is token error ", e);
            return null;
        }
    }

    public String getTokenTypeByToken(String token) {
        try {
            Claims claims = parseClaims(token);
            Object tokenType = claims.get(Const.TokenTypeClaimType);
            return tokenType == null ? "" : tokenType.toString();
        } catch (Exception e) {
            logger.debug("validate is token error ", e);
            return "";
        }
    }

    public boolean isAccessToken(String token) {
        return Const.AccessTokenType.equalsIgnoreCase(getTokenTypeByToken(token));
    }

    public boolean isRefreshToken(String token) {
        return Const.RefreshTokenType.equalsIgnoreCase(getTokenTypeByToken(token));
    }

    public boolean isRememberMeToken(String token) {
        try {
            Claims claims = parseClaims(token);
            Object remember = claims.get(Const.RememberMeClaimType);
            if (remember instanceof Boolean value) {
                return value;
            }
            return "true".equalsIgnoreCase(String.valueOf(remember));
        } catch (Exception e) {
            logger.debug("validate is token error ", e);
            return false;
        }
    }

    public String getCodeByTokenBySso(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            logger.debug("validate is token error ", e);
            return null;
        }
    }

    public Date getExpirationByToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration();
        } catch (Exception e) {
            logger.debug("get expiration by token error", e);
            return null;
        }
    }

    /// <summary>
    /// 创建JWT凭证
    /// </summary>
    /// <param name="account"></param>
    /// <param name="tenantCode">租户code</param>
    /// <param name="appKey">应用程序端Key</param>
    /// <returns></returns>
    public AuthToken CreateToken(String userId, DeviceType deviceType, String code) throws Exception {
        if (StringUtils.isEmpty(code)) {
            throw new RuntimeException();
        }

        AuthToken authToken = createTokenPair(userId, code, true);
        if (StringUtils.isNotEmpty(authToken.getAccessToken())) {
            return authToken;
        }
        throw new LZException(ExceptionCode.Default, "[" + deviceType.getValue() + "]端,用户[" + code + "]生成凭证失败");
    }

    private String generateToken(
            String userId,
            String code,
            long expireSeconds,
            String tokenType,
            String refreshTokenId,
            boolean rememberMe) {
        Date nowDate = new Date();
        Date expireDate = new Date(nowDate.getTime() + expireSeconds * 1000);

        Claims claims = new DefaultClaims();
        claims.put(Const.UserAccountClaimType, code);
        claims.put("MacAddress", getMacAddressSafe());
        claims.put("UserId", userId);
        claims.put(Const.TokenTypeClaimType, tokenType);
        claims.put(Const.RememberMeClaimType, rememberMe);
        if (StringUtils.isNotBlank(refreshTokenId)) {
            claims.put(Const.RefreshTokenIdClaimType, refreshTokenId);
        }

        return Jwts.builder()
                .setHeaderParam("typ", "JWT")
                .setSubject(code)
                .setClaims(claims)
                .setIssuedAt(nowDate)
                .setExpiration(expireDate)
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    /**
     * token是否过期
     *
     * @return true：过期
     */
    public boolean isTokenExpired(Date expiration) {
        return expiration.before(new Date());
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpire() {
        return expire;
    }

    public void setExpire(long expire) {
        this.expire = expire;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public AuthToken refreshToken(String refreshToken, DeviceType deviceType) throws Exception {
        return refreshToken(refreshToken);
    }

    public AuthToken refreshToken(String refreshToken) throws Exception {
        if (!isRefreshToken(refreshToken)) {
            throw new LZException(ExceptionCode.Default, "invalid refresh token");
        }
        String code = getCodeByToken(refreshToken);
        String userId = getUserIdByToken(refreshToken);
        boolean rememberMe = isRememberMeToken(refreshToken);
        return createTokenPair(userId, code, rememberMe);
    }

    private String getMacAddressSafe() {
        try {
            return Config.getMacAddress();
        } catch (Exception e) {
            logger.debug("get mac address failed", e);
            return "";
        }
    }

    private Claims parseClaims(String token) {
        Jws<Claims> jws = Jwts.parser().setSigningKey(secret).parseClaimsJws(token);
        return jws.getBody();
    }
}
