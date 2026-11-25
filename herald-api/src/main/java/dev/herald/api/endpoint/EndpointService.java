package dev.herald.api.endpoint;

import dev.herald.api.config.HeraldProperties;
import dev.herald.api.web.BadRequestException;
import dev.herald.api.web.NotFoundException;
import dev.herald.core.crypto.SecretCipher;
import dev.herald.core.event.EventTypes;
import dev.herald.core.id.Ids;
import dev.herald.core.net.EndpointUrlGuard;
import dev.herald.core.signing.SigningSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointService {

    /** How long the previous secret keeps signing after a rotation, so receivers can roll over. */
    static final Duration ROTATION_OVERLAP = Duration.ofHours(24);

    private final EndpointRepository repository;
    private final SecretCipher cipher;
    private final EndpointUrlGuard urlGuard;
    private final Clock clock;
    private final int maxPerTenant;

    EndpointService(EndpointRepository repository, SecretCipher cipher, EndpointUrlGuard urlGuard, Clock clock, HeraldProperties properties) {
        this.repository = repository;
        this.cipher = cipher;
        this.urlGuard = urlGuard;
        this.clock = clock;
        this.maxPerTenant = properties.endpoints().maxPerTenant();
    }

    /** An endpoint plus its signing secret in clear — returned only on create, reveal and rotate. */
    public record WithSecret(Endpoint endpoint, String secret) {}

    @Transactional
    public WithSecret create(UUID tenantId, String url, String description, List<String> eventTypes) {
        if (repository.countByTenant(tenantId) >= maxPerTenant) {
            throw new BadRequestException("Endpoint limit of " + maxPerTenant + " reached for this tenant");
        }
        UUID id = Ids.newId();
        SigningSecret secret = SigningSecret.generate();
        Instant now = clock.instant();
        Endpoint endpoint = new Endpoint(
                id, tenantId, urlGuard.validate(url).toString(), description, normalise(eventTypes), EndpointStatus.ENABLED,
                cipher.encrypt(secret.encoded(), Endpoint.secretAad(tenantId, id)), null, null, now, now);
        repository.insert(endpoint);
        return new WithSecret(endpoint, secret.encoded());
    }

    public Endpoint get(UUID tenantId, UUID id) {
        return repository.find(tenantId, id).orElseThrow(() -> new NotFoundException("Endpoint", id));
    }

    public List<Endpoint> list(UUID tenantId) {
        return repository.findByTenant(tenantId);
    }

    @Transactional
    public Endpoint update(UUID tenantId, UUID id, String url, String description, List<String> eventTypes, EndpointStatus status) {
        Endpoint current = get(tenantId, id);
        Endpoint updated = new Endpoint(
                id,
                tenantId,
                url != null ? urlGuard.validate(url).toString() : current.url(),
                description != null ? description : current.description(),
                eventTypes != null ? normalise(eventTypes) : current.eventTypes(),
                status != null ? status : current.status(),
                current.secretCiphertext(),
                current.previousSecretCiphertext(),
                current.previousSecretExpiresAt(),
                current.createdAt(),
                clock.instant());
        repository.update(updated);
        return updated;
    }

    public void delete(UUID tenantId, UUID id) {
        if (!repository.delete(tenantId, id)) {
            throw new NotFoundException("Endpoint", id);
        }
    }

    public String revealSecret(UUID tenantId, UUID id) {
        Endpoint endpoint = get(tenantId, id);
        return cipher.decrypt(endpoint.secretCiphertext(), Endpoint.secretAad(tenantId, id));
    }

    /**
     * Issues a new secret. The previous one stays valid for {@link #ROTATION_OVERLAP}; during that
     * window deliveries carry both signatures.
     */
    @Transactional
    public WithSecret rotateSecret(UUID tenantId, UUID id) {
        Endpoint current = get(tenantId, id);
        SigningSecret next = SigningSecret.generate();
        Instant now = clock.instant();
        Endpoint rotated = new Endpoint(
                id, tenantId, current.url(), current.description(), current.eventTypes(), current.status(),
                cipher.encrypt(next.encoded(), Endpoint.secretAad(tenantId, id)),
                current.secretCiphertext(),
                now.plus(ROTATION_OVERLAP),
                current.createdAt(),
                now);
        repository.update(rotated);
        return new WithSecret(rotated, next.encoded());
    }

    private static List<String> normalise(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new BadRequestException("eventTypes must contain at least one type or pattern");
        }
        var unique = new LinkedHashSet<String>();
        for (String type : eventTypes) {
            String trimmed = type == null ? "" : type.trim();
            if (!EventTypes.isValidPattern(trimmed)) {
                throw new BadRequestException("Invalid event type pattern: '" + trimmed + "'");
            }
            unique.add(trimmed);
        }
        return List.copyOf(unique);
    }
}
