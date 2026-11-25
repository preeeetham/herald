package dev.herald.api.tenant;

import dev.herald.api.apikey.ApiKeyService;
import dev.herald.api.apikey.IssuedApiKey;
import dev.herald.api.config.HeraldProperties;
import dev.herald.api.web.BadRequestException;
import dev.herald.api.web.ConflictException;
import dev.herald.api.web.NotFoundException;
import dev.herald.api.web.Page;
import dev.herald.core.id.Ids;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenants;
    private final ApiKeyService apiKeys;
    private final HeraldProperties.TenantDefaults defaults;

    TenantService(TenantRepository tenants, ApiKeyService apiKeys, HeraldProperties properties) {
        this.tenants = tenants;
        this.apiKeys = apiKeys;
        this.defaults = properties.tenantDefaults();
    }

    /** A new tenant together with its first API key, which is returned in clear exactly once. */
    public record Onboarded(Tenant tenant, IssuedApiKey apiKey) {}

    @Transactional
    public Onboarded create(String slug, String name, Integer rps, Integer burst, Integer maxConcurrency) {
        if (tenants.existsBySlug(slug)) {
            throw new ConflictException("Tenant slug '" + slug + "' is already taken");
        }
        int rate = rps != null ? rps : defaults.rateLimitPerSecond();
        int bucket = burst != null ? burst : Math.max(defaults.rateLimitBurst(), rate);
        validateLimits(rate, bucket);
        Instant now = Instant.now();
        Tenant tenant = new Tenant(
                Ids.newId(), slug, name, TenantStatus.ACTIVE, rate, bucket,
                maxConcurrency != null ? maxConcurrency : defaults.maxConcurrency(), now, now);
        tenants.insert(tenant);
        return new Onboarded(tenant, apiKeys.issue(tenant.id(), "default"));
    }

    public Tenant get(UUID id) {
        return tenants.findById(id).orElseThrow(() -> new NotFoundException("Tenant", id));
    }

    public Page<Tenant> list(UUID after, int limit) {
        return Page.of(tenants.findPage(after, limit + 1), limit, Tenant::id);
    }

    @Transactional
    public Tenant update(UUID id, String name, TenantStatus status, Integer rps, Integer burst, Integer maxConcurrency) {
        Tenant current = get(id);
        Tenant updated = new Tenant(
                id,
                current.slug(),
                name != null ? name : current.name(),
                status != null ? status : current.status(),
                rps != null ? rps : current.rateLimitPerSecond(),
                burst != null ? burst : current.rateLimitBurst(),
                maxConcurrency != null ? maxConcurrency : current.maxConcurrency(),
                current.createdAt(),
                Instant.now());
        validateLimits(updated.rateLimitPerSecond(), updated.rateLimitBurst());
        tenants.update(updated);
        return updated;
    }

    private static void validateLimits(int rps, int burst) {
        if (burst < rps) {
            throw new BadRequestException("rateLimitBurst must be greater than or equal to rateLimitPerSecond");
        }
    }
}
