package dev.herald.api.apikey;

import dev.herald.api.web.NotFoundException;
import dev.herald.core.auth.ApiKey;
import dev.herald.core.id.Ids;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyService {

    private final ApiKeyRepository repository;

    ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    public IssuedApiKey issue(UUID tenantId, String name) {
        ApiKey key = ApiKey.generate();
        ApiKeyRecord stored = new ApiKeyRecord(Ids.newId(), tenantId, name, key.prefix(), key.hash(), Instant.now(), null, null);
        repository.insert(stored);
        return new IssuedApiKey(stored.id(), name, key.prefix(), key.value(), stored.createdAt());
    }

    public List<ApiKeyRecord> list(UUID tenantId) {
        return repository.findByTenant(tenantId);
    }

    public void revoke(UUID tenantId, UUID keyId) {
        if (!repository.revoke(tenantId, keyId)) {
            throw new NotFoundException("API key", keyId);
        }
    }

    /**
     * Resolves a raw bearer token to its active key record. Lookup is by the public prefix; the
     * hash comparison is constant-time.
     */
    public Optional<ApiKeyRecord> authenticate(String rawKey) {
        return ApiKey.parse(rawKey).flatMap(key -> repository
                .findByPrefix(key.prefix())
                .filter(stored -> !stored.isRevoked() && key.matches(stored.keyHash()))
                .map(stored -> {
                    repository.touch(stored.id());
                    return stored;
                }));
    }
}
