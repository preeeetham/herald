package dev.herald.worker.delivery;

import java.time.Instant;
import java.util.UUID;

/** Everything needed to perform one delivery attempt, loaded when the delivery is claimed. */
public record DeliveryJob(
        UUID deliveryId,
        UUID tenantId,
        UUID eventId,
        UUID endpointId,
        String eventType,
        String payload,
        Instant eventCreatedAt,
        String traceParent,
        String url,
        boolean endpointEnabled,
        byte[] secretCiphertext,
        byte[] previousSecretCiphertext,
        Instant previousSecretExpiresAt,
        boolean tenantActive,
        int tenantMaxConcurrency,
        int attemptCount,
        int attemptsInCycle) {

    /** The 1-based number of the attempt about to be made within the current retry cycle. */
    public int nextAttemptInCycle() {
        return attemptsInCycle + 1;
    }
}
