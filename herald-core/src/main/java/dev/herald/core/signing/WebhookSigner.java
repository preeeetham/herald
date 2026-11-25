package dev.herald.core.signing;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs and verifies webhook payloads according to the <a
 * href="https://www.standardwebhooks.com/">Standard Webhooks</a> specification.
 *
 * <p>The signed content is {@code "{msgId}.{timestampSeconds}.{body}"}. The signature header holds
 * one or more space-separated {@code v1,<base64>} entries so that secrets can be rotated without
 * breaking receivers.
 */
public final class WebhookSigner {

    public static final String HEADER_ID = "webhook-id";
    public static final String HEADER_TIMESTAMP = "webhook-timestamp";
    public static final String HEADER_SIGNATURE = "webhook-signature";

    private static final String VERSION = "v1";
    private static final String ALGORITHM = "HmacSHA256";

    private final Duration tolerance;

    public WebhookSigner() {
        this(Duration.ofMinutes(5));
    }

    public WebhookSigner(Duration tolerance) {
        this.tolerance = tolerance;
    }

    /** Builds the value of the {@code webhook-signature} header for every active secret. */
    public String signatureHeader(List<SigningSecret> secrets, String msgId, long timestamp, String body) {
        if (secrets.isEmpty()) {
            throw new IllegalArgumentException("At least one signing secret is required");
        }
        return secrets.stream()
                .map(secret -> VERSION + "," + sign(secret, msgId, timestamp, body))
                .collect(Collectors.joining(" "));
    }

    public String sign(SigningSecret secret, String msgId, long timestamp, String body) {
        byte[] content = (msgId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(hmac(secret.key(), content));
    }

    /**
     * Verifies a received webhook. Returns {@code true} when the timestamp is inside the tolerance
     * window and at least one {@code v1} signature in the header matches.
     */
    public boolean verify(
            SigningSecret secret, String msgId, String timestampHeader, String body, String signatureHeader, Instant now) {
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - timestamp) > tolerance.toSeconds()) {
            return false;
        }
        byte[] expected = sign(secret, msgId, timestamp, body).getBytes(StandardCharsets.UTF_8);
        for (String entry : signatureHeader.trim().split("\\s+")) {
            int comma = entry.indexOf(',');
            if (comma > 0 && VERSION.equals(entry.substring(0, comma))) {
                byte[] actual = entry.substring(comma + 1).getBytes(StandardCharsets.UTF_8);
                if (MessageDigest.isEqual(expected, actual)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static byte[] hmac(byte[] key, byte[] content) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(content);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
