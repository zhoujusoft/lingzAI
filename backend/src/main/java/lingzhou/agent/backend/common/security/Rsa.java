package lingzhou.agent.backend.common.security;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;

public class Rsa {

    private static final String PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAg5mHOvjOgVOKTbKa65NS4SCL0OXICC7TGtHSqefnOIdYXLIFipDt2WmQWvidbKmw8oQmxveinyG4ZPjpyokPjay/IrxBwf87xjZUzaEqWf21/tsvAl3SEebSW5sbfS1PMTaSIV+rciOf4WOsqLRJSkeFs5q8xoHzDTxRoRDBSQR0XD5xf7VZgaRRNXbf71aBDh/vqrzgm1HSAyKTlNsohO4kkX5FrWyon3dFhIQFlkO17b+2EUVm1G8JiP6CcsXdWwQhcczQDcTjdHJYvDXRMrf4Q0qimRCMtsWCxkfoiYDQU9G/+LA6Kjnlg7ksUQZGE1A8sdye60Oqk8CWrcMahQIDAQAB";

    private Rsa() {}

    public static String RsaPasswordNew(String password) throws Exception {
        byte[] encrypted = encrypt(password.getBytes(), PUBLIC_KEY);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static byte[] encrypt(byte[] content, String publicKey) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(publicKey);
        RSAPublicKey pubKey = (RSAPublicKey)
                KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        return cipher.doFinal(content);
    }
}
