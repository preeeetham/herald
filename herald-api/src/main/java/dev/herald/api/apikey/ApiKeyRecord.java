package dev.herald.api.apikey;

import java.time.Instant;
import java.util.UUID;

/** Stored metadata of an API key. The key itself is never stored, only its hash. */
public record ApiKeyRecord(
        UUID id, UUID tenantId, String name, String prefix, String keyHash, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
