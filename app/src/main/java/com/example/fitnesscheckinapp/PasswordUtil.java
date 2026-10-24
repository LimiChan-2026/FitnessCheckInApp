package com.example.fitnesscheckinapp;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 客户端密码安全：与服务端一致的 PBKDF2 加盐哈希。
 * <p>
 * 用途：本地 user_info 表不再存明文密码，而是存 "salt$hash"，
 * 服务器不可用时仍可离线登录校验。
 */
public final class PasswordUtil {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int ITERATIONS = 12000;

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    /** 生成随机盐并计算哈希，返回 "salt$hash" */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return toHex(salt) + "$" + toHex(pbkdf2(password, salt));
    }

    /** 校验明文密码与 "salt$hash" 是否匹配 */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null || !stored.contains("$")) {
            return false;
        }
        try {
            String[] parts = stored.split("\\$");
            byte[] salt = fromHex(parts[0]);
            byte[] expected = fromHex(parts[1]);
            byte[] actual = pbkdf2(password, salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, ITERATIONS, HASH_LENGTH * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 不可用", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX[(b >> 4) & 0xf]).append(HEX[b & 0xf]);
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
