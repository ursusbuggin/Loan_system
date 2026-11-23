package loansavings;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted PBKDF2 password hashing. Stored form: salt$hash (hex). */
final class Security {
    private static final int ITERATIONS = 100_000;

    private Security() {}

    static String hash(String password) {
        byte[] salt = new byte[8];
        new SecureRandom().nextBytes(salt);
        return HexFormat.of().formatHex(salt) + "$" + derive(password, salt);
    }

    static boolean verify(String password, String stored) {
        int i = stored.indexOf('$');
        if (i < 0) return false;
        byte[] salt = HexFormat.of().parseHex(stored.substring(0, i));
        String expected = stored.substring(i + 1);
        return MessageDigest.isEqual(derive(password, salt).getBytes(), expected.getBytes());
    }

    private static String derive(String password, byte[] salt) {
        try {
            var spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(key);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
