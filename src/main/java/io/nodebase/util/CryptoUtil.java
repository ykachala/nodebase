package io.nodebase.util;

import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;

public final class CryptoUtil {

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int BCRYPT_ROUNDS = 12;

    private CryptoUtil() {
    }

    /** Generate {@code byteLength} random bytes and return them as lowercase hex. */
    public static String randomToken(int byteLength) {
        if (byteLength <= 0) throw new IllegalArgumentException("byteLength must be > 0");
        byte[] buf = new byte[byteLength];
        RNG.nextBytes(buf);
        return toHex(buf);
    }

    public static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    public static byte[] fromHex(String hex) {
        if (hex == null) throw new IllegalArgumentException("hex is null");
        int len = hex.length();
        if ((len & 1) != 0) throw new IllegalArgumentException("hex length must be even");
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    /** BCrypt hash for the given plaintext password. */
    public static String hashPassword(String plaintext) {
        if (plaintext == null) throw new IllegalArgumentException("password is null");
        return BCrypt.hashpw(plaintext, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    /** Verify a plaintext password against a stored BCrypt hash. */
    public static boolean verifyPassword(String plaintext, String hash) {
        if (plaintext == null || hash == null) return false;
        try {
            return BCrypt.checkpw(plaintext, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
