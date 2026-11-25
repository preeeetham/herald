package dev.herald.core.signing;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * A symmetric webhook signing secret in Standard Webhooks format: {@code whsec_<base64 key>}.
 */
public final class SigningSecret {

    public static final String PREFIX = "whsec_";
    private static final int KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;

    private SigningSecret(byte[] key) {
        if (key.length < 24 || key.length > 64) {
            throw new IllegalArgumentException("Signing key must be between 24 and 64 bytes");
        }
        this.key = key.clone();
    }

    public static SigningSecret generate() {
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        return new SigningSecret(key);
    }

    public static SigningSecret parse(String encoded) {
        if (encoded == null || !encoded.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Signing secret must start with " + PREFIX);
        }
        try {
            return new SigningSecret(Base64.getDecoder().decode(encoded.substring(PREFIX.length())));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Signing secret is not valid base64", e);
        }
    }

    byte[] key() {
        return key.clone();
    }

    /** The wire format, shown to the tenant exactly once. */
    public String encoded() {
        return PREFIX + Base64.getEncoder().encodeToString(key);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SigningSecret other && Arrays.equals(key, other.key);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(key);
    }

    @Override
    public String toString() {
        return "SigningSecret[****]";
    }
}
