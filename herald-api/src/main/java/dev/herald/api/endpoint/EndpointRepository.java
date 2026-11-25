package dev.herald.api.endpoint;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EndpointRepository {

    private static final String COLUMNS = """
            id, tenant_id, url, description, event_types, status, secret_ciphertext,
            previous_secret_ciphertext, previous_secret_expires_at, created_at, updated_at""";

    private final JdbcClient jdbc;

    EndpointRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(Endpoint e) {
        jdbc.sql("""
                INSERT INTO endpoint (id, tenant_id, url, description, event_types, status, secret_ciphertext)
                VALUES (:id, :tenantId, :url, :description, :eventTypes, :status, :secret)
                """)
                .param("id", e.id())
                .param("tenantId", e.tenantId())
                .param("url", e.url())
                .param("description", e.description())
                .param("eventTypes", e.eventTypes().toArray(String[]::new))
                .param("status", e.status().name())
                .param("secret", e.secretCiphertext())
                .update();
    }

    void update(Endpoint e) {
        jdbc.sql("""
                UPDATE endpoint SET url = :url, description = :description, event_types = :eventTypes, status = :status,
                       secret_ciphertext = :secret, previous_secret_ciphertext = :previousSecret,
                       previous_secret_expires_at = :previousExpiresAt, updated_at = now()
                WHERE id = :id AND tenant_id = :tenantId
                """)
                .param("id", e.id())
                .param("tenantId", e.tenantId())
                .param("url", e.url())
                .param("description", e.description())
                .param("eventTypes", e.eventTypes().toArray(String[]::new))
                .param("status", e.status().name())
                .param("secret", e.secretCiphertext())
                .param("previousSecret", e.previousSecretCiphertext())
                .param("previousExpiresAt", e.previousSecretExpiresAt() == null ? null : Timestamp.from(e.previousSecretExpiresAt()))
                .update();
    }

    public Optional<Endpoint> find(UUID tenantId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM endpoint WHERE id = :id AND tenant_id = :tenantId")
                .param("id", id)
                .param("tenantId", tenantId)
                .query(EndpointRepository::map)
                .optional();
    }

    public List<Endpoint> findByTenant(UUID tenantId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM endpoint WHERE tenant_id = :tenantId ORDER BY id")
                .param("tenantId", tenantId)
                .query(EndpointRepository::map)
                .list();
    }

    public List<Endpoint> findEnabledByTenant(UUID tenantId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM endpoint WHERE tenant_id = :tenantId AND status = 'ENABLED'")
                .param("tenantId", tenantId)
                .query(EndpointRepository::map)
                .list();
    }

    int countByTenant(UUID tenantId) {
        return jdbc.sql("SELECT count(*) FROM endpoint WHERE tenant_id = :tenantId")
                .param("tenantId", tenantId)
                .query(Integer.class)
                .single();
    }

    boolean delete(UUID tenantId, UUID id) {
        return jdbc.sql("DELETE FROM endpoint WHERE id = :id AND tenant_id = :tenantId")
                .param("id", id)
                .param("tenantId", tenantId)
                .update() == 1;
    }

    private static Endpoint map(ResultSet rs, int row) throws SQLException {
        Array types = rs.getArray("event_types");
        Timestamp previousExpiry = rs.getTimestamp("previous_secret_expires_at");
        return new Endpoint(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("url"),
                rs.getString("description"),
                Arrays.asList((String[]) types.getArray()),
                EndpointStatus.valueOf(rs.getString("status")),
                rs.getBytes("secret_ciphertext"),
                rs.getBytes("previous_secret_ciphertext"),
                previousExpiry == null ? null : previousExpiry.toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

}
