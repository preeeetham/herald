package dev.herald.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed configuration for the API, bound from {@code herald.*}. */
@Validated
@ConfigurationProperties("herald")
public record HeraldProperties(
        @NotBlank String adminToken,
        @Valid @NotNull Secrets secrets,
        @Valid @NotNull Endpoints endpoints,
        @Valid @NotNull TenantDefaults tenantDefaults,
        @Valid @NotNull Events events,
        @Valid @NotNull Cors cors) {

    /** {@code encryptionKey} is a base64 AES-256 key, injected from a KMS-backed secret in production. */
    public record Secrets(@NotBlank String encryptionKey) {}

    public record Endpoints(boolean allowPrivateNetworks, boolean requireHttps, @Min(1) int maxPerTenant) {}

    public record TenantDefaults(@Min(1) int rateLimitPerSecond, @Min(1) int rateLimitBurst, @Min(1) int maxConcurrency) {}

    public record Events(@Min(1024) int maxPayloadBytes) {}

    public record Cors(List<String> allowedOrigins) {}
}
