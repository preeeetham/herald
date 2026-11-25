package dev.herald.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Tenant API key of the form {@code hk_<prefix>_<secret>}.
 *
 * <p>The prefix is stored in clear and indexed for lookup; only the SHA-256 hash of the full key
 * is stored. Keys have 190 bits of entropy, so a fast hash is appropriate (no need for bcrypt).
 */
public record ApiKey(String prefix, String secret) {

    private static final String SCHEME = "hk";
    private static final String PREFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String SECRET_ALPHABET = PREFIX_ALPHABET + "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Pattern FORMAT = Pattern.compile("^hk_([a-z0-9]{8})_([A-Za-z0-9]{32})$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public static ApiKey generate() {
        return new ApiKey(randomString(8, PREFIX_ALPHABET), randomString(32, SECRET_ALPHABET));
    }

    public static Optional<ApiKey> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        var matcher = FORMAT.matcher(raw.trim());
        return matcher.matches() ? Optional.of(new ApiKey(matcher.group(1), matcher.group(2))) : Optional.empty();
    }

    /** The full key, shown to the tenant exactly once at creation. */
    public String value() {
        return SCHEME + "_" + prefix + "_" + secret;
    }

    public String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time comparison against a stored hash. */
    public boolean matches(String storedHash) {
        return MessageDigest.isEqual(
                hash().getBytes(StandardCharsets.US_ASCII), storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public String toString() {
        return SCHEME + "_" + prefix + "_****";
    }

    private static String randomString(int length, String alphabet) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
