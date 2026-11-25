package dev.herald.core.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private static final String MSG_ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
    private static final long TIMESTAMP = 1614265330L;
    private static final String BODY = "{\"test\": 2432232314}";

    private final WebhookSigner signer = new WebhookSigner();

    /**
     * Public test vector from the Standard Webhooks specification — not a real credential. The
     * prefix is concatenated at runtime so secret scanners do not mistake it for a live key.
     */
    private static final String SPEC_TEST_KEY = "MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw"; // gitleaks:allow

    @Test
    void matchesStandardWebhooksReferenceVector() {
        SigningSecret secret = SigningSecret.parse(SigningSecret.PREFIX + SPEC_TEST_KEY);

        String header = signer.signatureHeader(List.of(secret), MSG_ID, TIMESTAMP, BODY);

        assertThat(header).isEqualTo("v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=");
    }

    @Test
    void verifiesItsOwnSignature() {
        SigningSecret secret = SigningSecret.generate();
        String header = signer.signatureHeader(List.of(secret), MSG_ID, TIMESTAMP, BODY);

        boolean valid = signer.verify(secret, MSG_ID, Long.toString(TIMESTAMP), BODY, header, Instant.ofEpochSecond(TIMESTAMP + 60));

        assertThat(valid).isTrue();
    }

    @Test
    void rejectsTamperedBody() {
        SigningSecret secret = SigningSecret.generate();
        String header = signer.signatureHeader(List.of(secret), MSG_ID, TIMESTAMP, BODY);

        assertThat(signer.verify(secret, MSG_ID, Long.toString(TIMESTAMP), BODY + " ", header, Instant.ofEpochSecond(TIMESTAMP)))
                .isFalse();
    }

    @Test
    void rejectsTimestampOutsideToleranceToPreventReplay() {
        SigningSecret secret = SigningSecret.generate();
        String header = signer.signatureHeader(List.of(secret), MSG_ID, TIMESTAMP, BODY);

        assertThat(signer.verify(secret, MSG_ID, Long.toString(TIMESTAMP), BODY, header, Instant.ofEpochSecond(TIMESTAMP + 301)))
                .isFalse();
    }

    @Test
    void emitsOneSignaturePerSecretDuringRotation() {
        SigningSecret current = SigningSecret.generate();
        SigningSecret previous = SigningSecret.generate();

        String header = signer.signatureHeader(List.of(current, previous), MSG_ID, TIMESTAMP, BODY);

        assertThat(header.split(" ")).hasSize(2);
        assertThat(signer.verify(previous, MSG_ID, Long.toString(TIMESTAMP), BODY, header, Instant.ofEpochSecond(TIMESTAMP)))
                .isTrue();
    }

    @Test
    void secretRoundTripsThroughWireFormatAndNeverPrintsKey() {
        SigningSecret secret = SigningSecret.generate();

        assertThat(SigningSecret.parse(secret.encoded())).isEqualTo(secret);
        assertThat(secret.toString()).doesNotContain(secret.encoded());
    }

    @Test
    void rejectsSecretWithoutPrefix() {
        assertThatThrownBy(() -> SigningSecret.parse("abc")).isInstanceOf(IllegalArgumentException.class);
    }
}
