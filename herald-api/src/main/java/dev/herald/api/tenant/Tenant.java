package dev.herald.api.tenant;

import java.time.Instant;
import java.util.UUID;

public record Tenant(
        UUID id,
        String slug,
        String name,
        TenantStatus status,
        int rateLimitPerSecond,
        int rateLimitBurst,
        int maxConcurrency,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }
}
