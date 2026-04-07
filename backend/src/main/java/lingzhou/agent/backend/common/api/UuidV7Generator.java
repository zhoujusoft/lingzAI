package lingzhou.agent.backend.common.api;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private UuidV7Generator() {}

    public static String next() {
        byte[] bytes = new byte[16];

        long timestamp = System.currentTimeMillis();
        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) timestamp;

        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        bytes[6] = (byte) (0x70 | (random[0] & 0x0F));
        bytes[7] = random[1];
        bytes[8] = (byte) (0x80 | (random[2] & 0x3F));
        System.arraycopy(random, 3, bytes, 9, 7);

        String hex = HEX.formatHex(bytes);
        return hex.substring(0, 8)
                + "-"
                + hex.substring(8, 12)
                + "-"
                + hex.substring(12, 16)
                + "-"
                + hex.substring(16, 20)
                + "-"
                + hex.substring(20, 32);
    }
}
