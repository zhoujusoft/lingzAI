package lingzhou.agent.backend.business.chat.util;

import java.security.SecureRandom;

public final class UlidGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private UlidGenerator() {}

    public static String next() {
        long time = System.currentTimeMillis();
        char[] chars = new char[26];

        for (int i = 9; i >= 0; i--) {
            chars[i] = ALPHABET[(int) (time & 31)];
            time >>>= 5;
        }

        byte[] random = new byte[16];
        RANDOM.nextBytes(random);

        int index = 10;
        int buffer = 0;
        int bits = 0;

        for (byte value : random) {
            buffer = (buffer << 8) | (value & 0xFF);
            bits += 8;
            while (bits >= 5 && index < chars.length) {
                bits -= 5;
                chars[index++] = ALPHABET[(buffer >>> bits) & 31];
            }
        }

        if (index < chars.length && bits > 0) {
            chars[index++] = ALPHABET[(buffer << (5 - bits)) & 31];
        }

        while (index < chars.length) {
            chars[index++] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }

        return new String(chars);
    }
}
