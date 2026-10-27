package app.cleanyourtv.home;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted PBKDF2 pin. Five failures start a 30s wait that doubles up to 5 minutes. */
public final class PinBook {
    public int fails;
    public long lockUntil;
    public String saltHex = "";
    public String hashHex = "";
    public int iterations;

    public boolean hasPin() {
        return hashHex != null && !hashHex.isEmpty();
    }

    public static boolean digits(String pin) {
        if (pin == null || pin.length() < 4 || pin.length() > 6) return false;
        for (int i = 0; i < pin.length(); i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    public static PinBook create(String pin) {
        return create(pin, 200_000);
    }

    public static PinBook create(String pin, int iterations) {
        if (!digits(pin) || iterations < 1) return null;
        try {
            PinBook book = new PinBook();
            book.iterations = iterations;
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            book.saltHex = hex(salt);
            book.hashHex = hex(pbkdf2(pin, salt, iterations));
            return book;
        } catch (Exception e) {
            return null;
        }
    }

    /** ok, bad, or locked. A locked book does not add another failure. */
    public String verify(String pin, long nowMs) {
        if (nowMs < lockUntil) return "locked";
        if (!hasPin() || iterations < 1) return "bad";
        try {
            byte[] got = pbkdf2(pin == null ? "" : pin, unhex(saltHex), iterations);
            if (MessageDigest.isEqual(got, unhex(hashHex))) {
                fails = 0;
                lockUntil = 0;
                return "ok";
            }
        } catch (Exception e) {
            return "bad";
        }
        fails++;
        if (fails >= 5) {
            int shift = Math.min(fails - 5, 4);
            long wait = Math.min(300_000L, 30_000L << shift);
            lockUntil = nowMs + wait;
        }
        return "bad";
    }

    public static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte v : bytes) s.append(String.format(Locale.US, "%02x", v & 0xff));
        return s.toString();
    }

    public static String randomHex(int chars) {
        byte[] bytes = new byte[chars / 2];
        new SecureRandom().nextBytes(bytes);
        return hex(bytes);
    }

    private static byte[] pbkdf2(String pin, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] unhex(String hex) {
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
