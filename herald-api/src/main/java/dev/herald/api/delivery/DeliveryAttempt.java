package dev.herald.api.delivery;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAttempt(
        UUID id,
        int attemptNumber,
        String outcome,
        Integer statusCode,
        String error,
        String responseExcerpt,
        Instant startedAt,
        int durationMs) {}
