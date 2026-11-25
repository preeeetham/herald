package dev.herald.worker.support;

import dev.herald.core.crypto.EndpointSecrets;
import dev.herald.core.crypto.SecretCipher;
import dev.herald.core.id.Ids;
import dev.herald.core.signing.SigningSecret;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Writes control-plane and data-plane rows directly, the way the API would. */
@Component
public class Fixtures {

    public record Endpoint(UUID id, UUID tenantId, SigningSecret secret) {}

    private final JdbcClient jdbc;
    private final SecretCipher cipher;

    Fixtures(JdbcClient jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    public UUID tenant(int maxConcurrency) {
        UUID id = Ids.newId();
        jdbc.sql("""
                INSERT INTO tenant (id, slug, name, rate_limit_per_second, rate_limit_burst, max_concurrency)
                VALUES (:id, :slug, 'Test Insurance', 100, 100, :concurrency)
                """)
                .param("id", id)
                .param("slug", "t-" + id.toString().substring(24))
                .param("concurrency", maxConcurrency)
                .update();
        return id;
    }

    public Endpoint endpoint(UUID tenantId, String url) {
        return endpoint(tenantId, url, null);
    }

    /** @param previous a rotated-out secret still inside its overlap window, or {@code null} */
    public Endpoint endpoint(UUID tenantId, String url, SigningSecret previous) {
        UUID id = Ids.newId();
        SigningSecret secret = SigningSecret.generate();
        String aad = EndpointSecrets.associatedData(tenantId, id);
        jdbc.sql("""
                INSERT INTO endpoint (id, tenant_id, url, event_types, secret_ciphertext,
                                      previous_secret_ciphertext, previous_secret_expires_at)
                VALUES (:id, :tenantId, :url, ARRAY['*'], :secret, :previous, :previousExpiry)
                """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("url", url)
                .param("secret", cipher.encrypt(secret.encoded(), aad))
                .param("previous", previous == null ? null : cipher.encrypt(previous.encoded(), aad))
                .param("previousExpiry", previous == null ? null : Timestamp.from(Instant.now().plusSeconds(3600)))
                .update();
        return new Endpoint(id, tenantId, secret);
    }

    public void disable(UUID endpointId) {
        jdbc.sql("UPDATE endpoint SET status = 'DISABLED' WHERE id = :id").param("id", endpointId).update();
    }

    /** Publishes an event with one delivery in the given status; returns the delivery id. */
    public UUID delivery(Endpoint endpoint, String status) {
        UUID eventId = Ids.newId();
        jdbc.sql("""
                INSERT INTO event (id, tenant_id, type, payload)
                VALUES (:id, :tenantId, 'claim.created', CAST(:payload AS jsonb))
                """)
                .param("id", eventId)
                .param("tenantId", endpoint.tenantId())
                .param("payload", "{\"claimNumber\": \"CLM-" + eventId.toString().substring(30) + "\"}")
                .update();
        UUID deliveryId = Ids.newId();
        jdbc.sql("""
                INSERT INTO delivery (id, tenant_id, event_id, endpoint_id, status)
                VALUES (:id, :tenantId, :eventId, :endpointId, :status)
                """)
                .param("id", deliveryId)
                .param("tenantId", endpoint.tenantId())
                .param("eventId", eventId)
                .param("endpointId", endpoint.id())
                .param("status", status)
                .update();
        return deliveryId;
    }

    public String status(UUID deliveryId) {
        return jdbc.sql("SELECT status FROM delivery WHERE id = :id").param("id", deliveryId).query(String.class).single();
    }

    public int attempts(UUID deliveryId) {
        return jdbc.sql("SELECT count(*) FROM delivery_attempt WHERE delivery_id = :id").param("id", deliveryId).query(Integer.class).single();
    }

    public String attemptOutcome(UUID deliveryId, int attemptNumber) {
        return jdbc.sql("SELECT outcome || ':' || COALESCE(error, '') FROM delivery_attempt WHERE delivery_id = :id AND attempt_number = :n")
                .param("id", deliveryId)
                .param("n", attemptNumber)
                .query(String.class)
                .single();
    }

    public void ageInFlight(UUID deliveryId, String status, int minutesAgo) {
        jdbc.sql("UPDATE delivery SET status = :status, updated_at = now() - make_interval(mins => :mins) WHERE id = :id")
                .param("status", status)
                .param("mins", minutesAgo)
                .param("id", deliveryId)
                .update();
    }
}
