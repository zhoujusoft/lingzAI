package lingzhou.agent.backend.capability.dataset.runtime;

import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LowcodeSqlCryptoService {

    private static final String DES_KEY = "iamdespk";
    private static final String TRANSFORMATION = "DES/CBC/PKCS5Padding";

    public String encryptSelectSql(String sql) throws TaskException {
        if (!StringUtils.hasText(sql)) {
            throw new TaskException("SQL 不能为空", TaskException.Code.UNKNOWN);
        }
        try {
            byte[] keyBytes = DES_KEY.getBytes(StandardCharsets.UTF_8);
            DESKeySpec keySpec = new DESKeySpec(keyBytes);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
            SecretKey secretKey = keyFactory.generateSecret(keySpec);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec iv = new IvParameterSpec(keyBytes);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
            byte[] encrypted = cipher.doFinal(sql.getBytes(StandardCharsets.UTF_8));
            return toHex(encrypted);
        } catch (Exception ex) {
            throw new TaskException("低代码 SQL 加密失败", TaskException.Code.UNKNOWN, ex);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            String hex = Integer.toHexString(value & 0xFF);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }
}
