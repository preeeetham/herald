package dev.herald.api.tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String slug,
        String name,
        TenantStatus status,
        int rateLimitPerSecond,
        int rateLimitBurst,
        int maxConcurrency,
        Instant createdAt) {

    public static TenantResponse from(Tenant t) {
        return new TenantResponse(
                t.id(), t.slug(), t.name(), t.status(), t.rateLimitPerSecond(), t.rateLimitBurst(), t.maxConcurrency(), t.createdAt());
    }
}
