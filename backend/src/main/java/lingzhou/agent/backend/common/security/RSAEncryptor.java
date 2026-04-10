package lingzhou.agent.backend.common.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.springframework.util.StringUtils;

public class RSAEncryptor {

    private PrivateKey _privateKeyRsaProvider;

    private PublicKey _publicKeyRsaProvider;

    private static String publicKeyStr =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAg5mHOvjOgVOKTbKa65NS4SCL0OXICC7TGtHSqefnOIdYXLIFipDt2WmQWvidbKmw8oQmxveinyG4ZPjpyokPjay/IrxBwf87xjZUzaEqWf21/tsvAl3SEebSW5sbfS1PMTaSIV+rciOf4WOsqLRJSkeFs5q8xoHzDTxRoRDBSQR0XD5xf7VZgaRRNXbf71aBDh/vqrzgm1HSAyKTlNsohO4kkX5FrWyon3dFhIQFlkO17b+2EUVm1G8JiP6CcsXdWwQhcczQDcTjdHJYvDXRMrf4Q0qimRCMtsWCxkfoiYDQU9G/+LA6Kjnlg7ksUQZGE1A8sdye60Oqk8CWrcMahQIDAQAB";
    private static String privateKeyStr =
            "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCDmYc6+M6BU4pNsprrk1LhIIvQ5cgILtMa0dKp5+c4h1hcsgWKkO3ZaZBa+J1sqbDyhCbG96KfIbhk+OnKiQ+NrL8ivEHB/zvGNlTNoSpZ/bX+2y8CXdIR5tJbmxt9LU8xNpIhX6tyI5/hY6yotElKR4WzmrzGgfMNPFGhEMFJBHRcPnF/tVmBpFE1dt/vVoEOH++qvOCbUdIDIpOU2yiE7iSRfkWtbKifd0WEhAWWQ7Xtv7YRRWbUbwmI/oJyxd1bBCFxzNANxON0cli8NdEyt/hDSqKZEIy2xYLGR+iJgNBT0b/4sDoqOeWDuSxRBkYTUDyx3J7rQ6qTwJatwxqFAgMBAAECggEAP/3b4DU0VXbF/donsv3Eg2xMEJxrlG8QgC4ffjciHD7UHN8ECslGnz3R0CqsgjZRsNvJ715jWXYQMClJpg7X3VBu8PkSEL+H1W599i+0ZGYWpYL3bPMqP0I6cAkQaOIrbAbZMBlRSQNBr1vEjZR9Pv1gamGIAg8WnQ0DtIptMo+b4tTQQtMEu3qPP+s8VNx16CYy67Xb8zvQB/DxBa2G8YH6T4Ymiit+bF0WLSGhq035Tp7l6qnaYuD3PXFOwQFJ4uEp72uUDC7ahYQdhxPzj74EOtzFzBrZl4fRSBmMOKACwsY9HCPtj4eCdjrxqTij6awG65dckBSAxR5ZKnvA0QKBgQDrd4KoEpwzI3wwC4I2m9ow26DKBC29QiSgLhMICBmvx2EGw8emwkhDe9qQsaMRR++1GhnJQjk6lpXoavFhDPEetykQeidl319N6x84XE8lSBmsvl9+VzNnuNgANYDe8z5A9bIOB8y778K/GU+ympZt6IBoGgycLcjukuPSeAHEzwKBgQCPE1HzEqaiDtv8UfDV7OFIm+w2YFffcYjwYVf20cGH6i0Ko/gvzTXe04J3PW6KOpoD3+OVRGW/+y2nL+mfVrp4G5bXo2Hol7Q5DlUP5agvRtuzVRjkoLbDvVy22Inzbh/k2ZnlZfBtx+eAZ6thUp1d+xA4JIjmb5TiKe9SGcKoawKBgCIMvSsazdWjSwETmPfWn72y9NRhuE+G8g7rurrNmRFPLpuw0f0uWQ7RKTEFTwRft60johj1Z5suTW03kcIsAe4kJF8KmwnjPD/jQTeAOb21aGkPenWlnHxiQXH+Sq1y1UzFesMGo7eFm07RFTJJonkwRulG4w3unQDK4wD7c54HAoGACWIQ+8n7V6F11+Rf0QfJ9oMTSLWkzaiafHlF1ZAICyvWbF44hpoEsGpaogtLVpFlTF/AgyeLLL4CTnab8bE0ZXAZwmaaozBfx2YjOfmrv/37ppUsHsJTeh7PF8a3tVlBijajGyJGVEzJ5+fkm2tZmrI+bBApUkN7FiH96fp6g6UCgYBb8rQPMvhCH3DAlLU0ZA0Z7yGm14NZw5Hk3dI6F+Nebw3ozv4JMHz+UXioUxbRQya+mCqlPXUaUnV0J49aGxT7uy2y6Lhzk7nxRKzGZsinqA/IHzR4mBt7ciVIUOmqvHwnQ81zqiq0MEhahjK7XO4fyeGcN5TCQDzu+Hh/KFrCPg==";

    private static final String RSA = "RSA";

    private static final int KEY_SIZE = 2048;

    public static String defaultPublicKey() {
        return publicKeyStr;
    }

    public static String RsaDecrypt(String encryptStr) throws Exception {
        RSAEncryptor _rsaEncrytor = new RSAEncryptor(privateKeyStr, publicKeyStr);
        return _rsaEncrytor.decrypt(encryptStr);
    }

    public static String RsaDecryptWithOaepSha256(String encryptStr) throws Exception {
        RSAEncryptor rsaEncryptor = new RSAEncryptor(privateKeyStr, publicKeyStr);
        return rsaEncryptor.decryptOaepSha256(encryptStr);
    }

    public static String RsaPasswordNew(String plainText) throws Exception {
        RSAEncryptor rsaEncryptor = new RSAEncryptor(privateKeyStr, publicKeyStr);
        return rsaEncryptor.encrypt(plainText);
    }

    public static String encryptWithPublicKey(String plainText, String publicKey) throws Exception {
        if (!StringUtils.hasText(publicKey)) {
            throw new IllegalArgumentException("publicKey 不能为空");
        }
        byte[] encrypted = encryptBytes(plainText.getBytes(StandardCharsets.UTF_8), publicKey.trim());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private static byte[] encryptBytes(byte[] content, String publicKey) throws Exception {
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKey);
        PublicKey rsaPublicKey =
                KeyFactory.getInstance(RSA).generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        Cipher cipher = Cipher.getInstance(RSA);
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        return cipher.doFinal(content);
    }

    public RSAEncryptor(String privateKey, String publicKey) throws Exception {
        // 加载公钥和私钥
        KeyPair keyPair = loadKeyPair(publicKey, privateKey);

        // 获取公钥和私钥
        this._publicKeyRsaProvider = keyPair.getPublic();
        this._privateKeyRsaProvider = keyPair.getPrivate();
    }

    public String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, _publicKeyRsaProvider);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public String decrypt(String encryptedText) throws Exception {
        return decrypt(encryptedText, "RSA");
    }

    public String decrypt(String encryptedText, String transformation) throws Exception {
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.DECRYPT_MODE, _privateKeyRsaProvider);
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }

    public String decryptOaepSha256(String encryptedText) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec spec =
                new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.DECRYPT_MODE, _privateKeyRsaProvider, spec);
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }

    public static KeyPair loadKeyPair(String publicKeyStr, String privateKeyStr) throws Exception {
        // 解码Base64编码的公钥和私钥字符串
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyStr);
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyStr);

        // 创建公钥和私钥的KeySpec对象
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);

        // 生成公钥和私钥
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

        return new KeyPair(publicKey, privateKey);
    }
}
