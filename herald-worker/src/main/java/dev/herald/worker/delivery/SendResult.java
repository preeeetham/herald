package dev.herald.worker.delivery;

import dev.herald.core.retry.FailureClassifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Result of one HTTP attempt.
 *
 * @param statusCode HTTP status, or {@code null} when no response was received
 * @param error human-readable failure reason, or {@code null} on success
 * @param permanent the failure cannot succeed on retry (e.g. 4xx, SSRF block)
 */
public record SendResult(
        Integer statusCode,
        String error,
        String responseExcerpt,
        Instant startedAt,
        Duration duration,
        Optional<Duration> retryAfter,
        boolean permanent) {

    public boolean succeeded() {
        return statusCode != null && FailureClassifier.isSuccess(statusCode);
    }

    static SendResult response(int status, String excerpt, Instant startedAt, Duration duration, Optional<Duration> retryAfter) {
        boolean ok = FailureClassifier.isSuccess(status);
        return new SendResult(
                status, ok ? null : "HTTP " + status, excerpt, startedAt, duration, retryAfter, !ok && !FailureClassifier.isRetryable(status));
    }

    static SendResult transientError(String error, Instant startedAt, Duration duration) {
        return new SendResult(null, error, null, startedAt, duration, Optional.empty(), false);
    }

    static SendResult permanentError(String error, Instant startedAt, Duration duration) {
        return new SendResult(null, error, null, startedAt, duration, Optional.empty(), true);
    }
}
