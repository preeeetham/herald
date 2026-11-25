package dev.herald.api.delivery;

import java.util.Set;

public enum DeliveryStatus {
    PENDING,
    QUEUED,
    DELIVERING,
    SUCCEEDED,
    DEAD;

    /** Only finished deliveries can be replayed; in-flight ones would be delivered twice. */
    public static final Set<DeliveryStatus> REPLAYABLE = Set.of(SUCCEEDED, DEAD);
}
