package dev.herald.api.endpoint;

import dev.herald.core.event.EventTypes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Endpoint(
        UUID id,
        UUID tenantId,
        String url,
        String description,
        List<String> eventTypes,
        EndpointStatus status,
        byte[] secretCiphertext,
        byte[] previousSecretCiphertext,
        Instant previousSecretExpiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean subscribesTo(String eventType) {
        return status == EndpointStatus.ENABLED && EventTypes.matchesAny(eventTypes, eventType);
    }

    /** Associated data binding the encrypted secret to this endpoint and tenant. */
    static String secretAad(UUID tenantId, UUID endpointId) {
        return tenantId + ":" + endpointId;
    }
}
