package dev.herald.api.event;

import java.time.Instant;
import java.util.UUID;

/** A published business event. {@code payload} is the raw JSON document as stored. */
public record Event(UUID id, UUID tenantId, String type, String payload, String idempotencyKey, Instant createdAt) {}
