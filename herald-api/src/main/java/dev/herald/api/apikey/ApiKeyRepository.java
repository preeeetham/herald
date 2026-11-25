package dev.herald.api.apikey;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ApiKeyRepository {

    private static final String COLUMNS = "id, tenant_id, name, prefix, key_hash, created_at, last_used_at, revoked_at";

    private final JdbcClient jdbc;

    ApiKeyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(ApiKeyRecord key) {
        jdbc.sql("INSERT INTO api_key (id, tenant_id, name, prefix, key_hash) VALUES (:id, :tenantId, :name, :prefix, :hash)")
                .param("id", key.id())
                .param("tenantId", key.tenantId())
                .param("name", key.name())
                .param("prefix", key.prefix())
                .param("hash", key.keyHash())
                .update();
    }

    Optional<ApiKeyRecord> findByPrefix(String prefix) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM api_key WHERE prefix = :prefix")
                .param("prefix", prefix)
                .query(ApiKeyRepository::map)
                .optional();
    }

    List<ApiKeyRecord> findByTenant(UUID tenantId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM api_key WHERE tenant_id = :tenantId ORDER BY id DESC")
                .param("tenantId", tenantId)
                .query(ApiKeyRepository::map)
                .list();
    }

    boolean revoke(UUID tenantId, UUID id) {
        return jdbc.sql("UPDATE api_key SET revoked_at = now() WHERE id = :id AND tenant_id = :tenantId AND revoked_at IS NULL")
                .param("id", id)
                .param("tenantId", tenantId)
                .update() == 1;
    }

    /** Coalesced to one write per key per minute so authentication does not write on every request. */
    void touch(UUID id) {
        jdbc.sql("""
                UPDATE api_key SET last_used_at = now()
                WHERE id = :id AND (last_used_at IS NULL OR last_used_at < now() - interval '1 minute')
                """)
                .param("id", id)
                .update();
    }

    private static ApiKeyRecord map(ResultSet rs, int row) throws SQLException {
        return new ApiKeyRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("name"),
                rs.getString("prefix"),
                rs.getString("key_hash"),
                rs.getTimestamp("created_at").toInstant(),
                instant(rs.getTimestamp("last_used_at")),
                instant(rs.getTimestamp("revoked_at")));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
