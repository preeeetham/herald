package dev.herald.core.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RetryAfterTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    @Test
    void parsesDelaySeconds() {
        assertThat(RetryAfter.parse("120", NOW)).contains(Duration.ofMinutes(2));
    }

    @Test
    void parsesHttpDate() {
        assertThat(RetryAfter.parse("Wed, 25 Nov 2026 10:05:00 GMT", NOW)).contains(Duration.ofMinutes(5));
    }

    @Test
    void pastDatesMeanRetryNow() {
        assertThat(RetryAfter.parse("Wed, 25 Nov 2026 09:00:00 GMT", NOW)).contains(Duration.ZERO);
    }

    @Test
    void ignoresGarbage() {
        assertThat(RetryAfter.parse(null, NOW)).isEmpty();
        assertThat(RetryAfter.parse("soon", NOW)).isEmpty();
        assertThat(RetryAfter.parse("-5", NOW)).isEmpty();
    }
}
