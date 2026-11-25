package dev.herald.api.delivery;

import java.time.Instant;
import java.util.UUID;

public record Delivery(
        UUID id,
        UUID tenantId,
        UUID eventId,
        UUID endpointId,
        String eventType,
        String endpointUrl,
        DeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Integer lastStatusCode,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {}
