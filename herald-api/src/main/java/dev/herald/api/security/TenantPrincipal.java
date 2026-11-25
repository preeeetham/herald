package dev.herald.api.security;

import java.util.UUID;

/** The authenticated tenant behind a request. All data access is scoped by {@link #tenantId()}. */
public record TenantPrincipal(UUID tenantId, String slug, UUID apiKeyId, int rateLimitPerSecond, int rateLimitBurst) {}
