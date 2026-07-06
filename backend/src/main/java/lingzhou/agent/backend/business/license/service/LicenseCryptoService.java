package lingzhou.agent.backend.business.license.service;

import com.alibaba.fastjson.JSON;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class LicenseCryptoService {

    private static final String PUBLIC_KEY_PEM =
            "-----BEGIN PUBLIC KEY-----\n"
                    + "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5C4ody4WuWXui00KYcsb\n"
                    + "ReL58CuuHRFNOQg+Dzkb1ng1ltacuFqG5RoIVjoZgABoV10qzhOYLocSxsTMBc9J\n"
                    + "s3q35HBD7sPV7mcF0vLveYQANtEmoRRhJzvZTlvGLBKwB/gt55WQT3U/SFpNSRmr\n"
                    + "n2EnNl2Jl0ybxVhCSkuwPw0x0xMjIcoZINjgZxJ6kl+SXL74qqXT+YF6doOu4sS1\n"
                    + "ii9BguVmdHqwlNFga8Nh966fnLTRPLgXQQHypk1PEkVcQbFISVWIw8/7j1DJ01GP\n"
                    + "JdjbXtDHOTsvGy/OYMPwJMzQoAj7AYdIWNhxpXg10aLNuAl3gO9tOoKYbudIyyv6\n"
                    + "XQIDAQAB\n"
                    + "-----END PUBLIC KEY-----";

    public ParsedLicenseEnvelope parseAndVerify(String rawLicense) {
        if (StringUtils.isBlank(rawLicense)) {
            throw new LicenseException(LicenseConstants.CODE_NOT_FOUND, "license 文件为空");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = JSON.parseObject(rawLicense, Map.class);
        if (envelope == null || envelope.isEmpty()) {
            throw new LicenseException(LicenseConstants.CODE_NOT_FOUND, "license 文件格式非法");
        }
        String alg = text(envelope.get("alg"));
        String payloadBase64 = text(envelope.get("payload"));
        String signatureBase64 = text(envelope.get("signature"));
        if (StringUtils.isBlank(alg) || StringUtils.isBlank(payloadBase64) || StringUtils.isBlank(signatureBase64)) {
            throw new LicenseException(LicenseConstants.CODE_NOT_FOUND, "license 缺少必要字段");
        }
        if (!"SHA256withRSA".equalsIgnoreCase(alg)) {
            throw new LicenseException(LicenseConstants.CODE_INVALID_SIGNATURE, "license 签名算法不受支持");
        }
        if (StringUtils.isBlank(payloadBase64) || StringUtils.isBlank(signatureBase64)) {
            if (envelope.containsKey("productCode") || envelope.containsKey("instanceCode")) {
                throw new LicenseException(
                        LicenseConstants.CODE_NOT_FOUND, "当前上传的是授权申请 JSON，不是 license 文件");
            }
            throw new LicenseException(LicenseConstants.CODE_NOT_FOUND, "license 缺少必要字段");
        }
        byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadBase64);
        byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureBase64);
        verifySignature(payloadBytes, signatureBytes, alg);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = JSON.parseObject(new String(payloadBytes, StandardCharsets.UTF_8), Map.class);
        if (payload == null || payload.isEmpty()) {
            throw new LicenseException(LicenseConstants.CODE_NOT_FOUND, "license payload 非法");
        }
        return new ParsedLicenseEnvelope(alg, payloadBase64, signatureBase64, payload);
    }

    private void verifySignature(byte[] payloadBytes, byte[] signatureBytes, String algorithm) {
        try {
            PublicKey publicKey = loadPublicKey();
            Signature signature = Signature.getInstance(algorithm);
            signature.initVerify(publicKey);
            signature.update(payloadBytes);
            if (!signature.verify(signatureBytes)) {
                throw new LicenseException(LicenseConstants.CODE_INVALID_SIGNATURE, "license 签名校验失败");
            }
        } catch (LicenseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LicenseException(LicenseConstants.CODE_INVALID_SIGNATURE, "license 签名校验失败");
        }
    }

    private PublicKey loadPublicKey() throws Exception {
        String normalized = PUBLIC_KEY_PEM.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record ParsedLicenseEnvelope(
            String algorithm, String payloadBase64, String signatureBase64, Map<String, Object> payload) {

        public List<String> featureFlags() {
            Object features = payload.get("features");
            if (features instanceof List<?> values) {
                return values.stream().map(String::valueOf).toList();
            }
            return List.of();
        }
    }
}
