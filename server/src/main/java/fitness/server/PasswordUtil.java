package fitness.server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 密码安全：PBKDF2 加盐哈希。
 * 存储格式：salt$hash（均为十六进制字符串），验证时按同样参数重算比对。
 * 采用 JDK 自带的 PBKDF2WithHmacSHA256，服务端零额外依赖。
 */
public final class PasswordUtil {

    private static final int SALT_LENGTH = 16;    // 盐字节数
    private static final int HASH_LENGTH = 32;   // 哈希字节数
    private static final int ITERATIONS = 12000; // 迭代次数

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    /** 生成随机盐并计算哈希，返回 "salt$hash" 格式的字符串用于入库 */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt);
        return toHex(salt) + "$" + toHex(hash);
    }

    /** 校验明文密码是否与库中 "salt$hash" 匹配 */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null || !stored.contains("$")) {
            return false;
        }
        try {
            String[] parts = stored.split("\\$");
            byte[] salt = fromHex(parts[0]);
            byte[] expected = fromHex(parts[1]);
            byte[] actual = pbkdf2(password, salt);
            return MessageDigest.isEqual(expected, actual); // 恒定时间比较，防时序攻击
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt) {
        try {
            javax.crypto.spec.PBEKeySpec spec =
                    new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, ITERATIONS, HASH_LENGTH * 8);
            javax.crypto.SecretKeyFactory factory =
                    javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException e) {
            throw new IllegalStateException("当前 JDK 不支持 PBKDF2WithHmacSHA256", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
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
