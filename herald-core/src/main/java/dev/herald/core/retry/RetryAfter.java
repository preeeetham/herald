package dev.herald.core.retry;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Parses an HTTP {@code Retry-After} header (RFC 9110 §10.2.3): delay-seconds or an HTTP-date. */
public final class RetryAfter {

    private RetryAfter() {}

    public static Optional<Duration> parse(String header, Instant now) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String value = header.trim();
        try {
            long seconds = Long.parseLong(value);
            return seconds < 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(seconds));
        } catch (NumberFormatException notSeconds) {
            try {
                Instant at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration delay = Duration.between(now, at);
                return Optional.of(delay.isNegative() ? Duration.ZERO : delay);
            } catch (DateTimeParseException notDate) {
                return Optional.empty();
            }
        }
    }
}
