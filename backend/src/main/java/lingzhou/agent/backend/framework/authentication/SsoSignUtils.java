package lingzhou.agent.backend.framework.authentication;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;

public final class SsoSignUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private SsoSignUtils() {}

    public static String buildPayload(
            String sourceSystem, String externalUserId, String phone, Long timestamp, String nonce) {
        return "sourceSystem=" + defaultString(sourceSystem)
                + "&externalUserId=" + defaultString(externalUserId)
                + "&phone=" + defaultString(phone)
                + "&timestamp=" + (timestamp == null ? "" : timestamp)
                + "&nonce=" + defaultString(nonce);
    }

    public static boolean verify(String payload, String secret, String sign) {
        if (StringUtils.isBlank(payload) || StringUtils.isBlank(secret) || StringUtils.isBlank(sign)) {
            return false;
        }
        return sign(payload, secret).equalsIgnoreCase(sign.trim());
    }

    private static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("生成 SSO 签名失败", ex);
        }
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
