package lingzhou.agent.backend.common.security;

import java.security.MessageDigest;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MD5Encryptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MD5Encryptor.class);

    private static final String EnglishChar = "abcdefghijklmnopqrstuvwxyz";

    private static final String SpecialChar = "~!@#$%^&*()";

    public static String GeneratePassword() {
        String password = "";
        Random r = new Random();
        password = password + "abcdefghijklmnopqrstuvwxyz".charAt(r.nextInt(25));
        password = password + "abcdefghijklmnopqrstuvwxyz".charAt(r.nextInt(25));
        password = password + r.nextInt(9);
        password = password + r.nextInt(9);
        password = password + "abcdefghijklmnopqrstuvwxyz".charAt(r.nextInt(25));
        password = password + "abcdefghijklmnopqrstuvwxyz".charAt(r.nextInt(25));
        password = password + r.nextInt(9);
        password = password + r.nextInt(9);
        password = password + "~!@#$%^&*()".charAt(r.nextInt(9));
        password = password + "~!@#$%^&*()".charAt(r.nextInt(9));
        return password;
    }

    public static String getMD5(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(data.getBytes());
        StringBuilder buf = new StringBuilder();
        byte[] bits = md.digest();
        for (byte bit : bits) {
            int a = bit;
            if (a < 0) a += 256;
            if (a < 16) buf.append("0");
            buf.append(Integer.toHexString(a));
        }
        return buf.toString();
    }
}
