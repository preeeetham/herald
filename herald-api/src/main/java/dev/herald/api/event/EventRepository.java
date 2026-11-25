package dev.herald.api.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class EventRepository {

    private static final String COLUMNS = "id, tenant_id, type, payload::text AS payload, idempotency_key, created_at";

    private final JdbcClient jdbc;

    EventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts the event unless the idempotency key was already used by this tenant.
     *
     * @return {@code true} if a new row was written
     */
    boolean insertIfAbsent(Event event, String traceParent) {
        return jdbc.sql("""
                INSERT INTO event (id, tenant_id, type, payload, idempotency_key, trace_parent)
                VALUES (:id, :tenantId, :type, CAST(:payload AS jsonb), :key, :traceParent)
                ON CONFLICT (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
                """)
                .param("id", event.id())
                .param("tenantId", event.tenantId())
                .param("type", event.type())
                .param("payload", event.payload())
                .param("key", event.idempotencyKey())
                .param("traceParent", traceParent)
                .update() == 1;
    }

    Optional<Event> findByIdempotencyKey(UUID tenantId, String key) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM event WHERE tenant_id = :tenantId AND idempotency_key = :key")
                .param("tenantId", tenantId)
                .param("key", key)
                .query(EventRepository::map)
                .optional();
    }

    /** Semantic JSON comparison (key order and whitespace do not matter). */
    boolean samePayload(UUID eventId, String type, String payload) {
        return jdbc.sql("SELECT type = :type AND payload = CAST(:payload AS jsonb) FROM event WHERE id = :id")
                .param("id", eventId)
                .param("type", type)
                .param("payload", payload)
                .query(Boolean.class)
                .single();
    }

    Optional<Event> find(UUID tenantId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM event WHERE id = :id AND tenant_id = :tenantId")
                .param("id", id)
                .param("tenantId", tenantId)
                .query(EventRepository::map)
                .optional();
    }

    List<Event> findPage(UUID tenantId, String type, UUID before, int limit) {
        return jdbc.sql("""
                SELECT %s FROM event
                WHERE tenant_id = :tenantId
                  AND (CAST(:type AS text) IS NULL OR type = :type)
                  AND (CAST(:before AS uuid) IS NULL OR id < :before)
                ORDER BY id DESC LIMIT :limit
                """.formatted(COLUMNS))
                .param("tenantId", tenantId)
                .param("type", type)
                .param("before", before)
                .param("limit", limit)
                .query(EventRepository::map)
                .list();
    }

    private static Event map(ResultSet rs, int row) throws SQLException {
        return new Event(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("type"),
                rs.getString("payload"),
                rs.getString("idempotency_key"),
                rs.getTimestamp("created_at").toInstant());
    }
}
