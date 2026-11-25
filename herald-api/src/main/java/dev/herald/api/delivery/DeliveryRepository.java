package dev.herald.api.delivery;

import dev.herald.core.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryRepository {

    private static final String SELECT = """
            SELECT d.id, d.tenant_id, d.event_id, d.endpoint_id, e.type AS event_type, ep.url AS endpoint_url,
                   d.status, d.attempt_count, d.next_attempt_at, d.last_status_code, d.last_error,
                   d.created_at, d.updated_at, d.completed_at
            FROM delivery d
            JOIN event e ON e.id = d.event_id
            JOIN endpoint ep ON ep.id = d.endpoint_id
            """;

    private static final String REPLAY_SET = """
            SET status = 'PENDING', attempts_in_cycle = 0, next_attempt_at = now(),
                completed_at = NULL, last_error = NULL, updated_at = now()
            """;

    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    DeliveryRepository(JdbcClient jdbc, JdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    /** Fan-out: one PENDING delivery per endpoint, written in the publishing transaction. */
    public void createPending(UUID tenantId, UUID eventId, List<UUID> endpointIds) {
        template.batchUpdate(
                "INSERT INTO delivery (id, tenant_id, event_id, endpoint_id) VALUES (?, ?, ?, ?)",
                endpointIds,
                endpointIds.size(),
                (ps, endpointId) -> {
                    ps.setObject(1, Ids.newId());
                    ps.setObject(2, tenantId);
                    ps.setObject(3, eventId);
                    ps.setObject(4, endpointId);
                });
    }

    public int countByEvent(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM delivery WHERE event_id = :eventId")
                .param("eventId", eventId)
                .query(Integer.class)
                .single();
    }

    public List<Delivery> findByEvent(UUID tenantId, UUID eventId) {
        return jdbc.sql(SELECT + " WHERE d.tenant_id = :tenantId AND d.event_id = :eventId ORDER BY d.id")
                .param("tenantId", tenantId)
                .param("eventId", eventId)
                .query(DeliveryRepository::map)
                .list();
    }

    Optional<Delivery> find(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " WHERE d.tenant_id = :tenantId AND d.id = :id")
                .param("tenantId", tenantId)
                .param("id", id)
                .query(DeliveryRepository::map)
                .optional();
    }

    List<Delivery> findPage(UUID tenantId, DeliveryStatus status, UUID endpointId, UUID before, int limit) {
        return jdbc.sql(SELECT + """
                WHERE d.tenant_id = :tenantId
                  AND (CAST(:status AS text) IS NULL OR d.status = :status)
                  AND (CAST(:endpointId AS uuid) IS NULL OR d.endpoint_id = :endpointId)
                  AND (CAST(:before AS uuid) IS NULL OR d.id < :before)
                ORDER BY d.id DESC LIMIT :limit
                """)
                .param("tenantId", tenantId)
                .param("status", status == null ? null : status.name())
                .param("endpointId", endpointId)
                .param("before", before)
                .param("limit", limit)
                .query(DeliveryRepository::map)
                .list();
    }

    List<DeliveryAttempt> findAttempts(UUID tenantId, UUID deliveryId) {
        return jdbc.sql("""
                SELECT id, attempt_number, outcome, status_code, error, response_excerpt, started_at, duration_ms
                FROM delivery_attempt WHERE tenant_id = :tenantId AND delivery_id = :deliveryId ORDER BY attempt_number
                """)
                .param("tenantId", tenantId)
                .param("deliveryId", deliveryId)
                .query((rs, row) -> new DeliveryAttempt(
                        rs.getObject("id", UUID.class),
                        rs.getInt("attempt_number"),
                        rs.getString("outcome"),
                        (Integer) rs.getObject("status_code"),
                        rs.getString("error"),
                        rs.getString("response_excerpt"),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getInt("duration_ms")))
                .list();
    }

    /** Resets a finished delivery with a fresh retry budget. Conditional, so it is race-free. */
    boolean replay(UUID tenantId, UUID id) {
        return jdbc.sql("UPDATE delivery " + REPLAY_SET + " WHERE id = :id AND tenant_id = :tenantId AND status IN ('SUCCEEDED', 'DEAD')")
                .param("id", id)
                .param("tenantId", tenantId)
                .update() == 1;
    }

    int replayMatching(UUID tenantId, DeliveryStatus status, UUID endpointId, Instant since, int max) {
        return jdbc.sql("UPDATE delivery " + REPLAY_SET + """
                WHERE id IN (
                    SELECT id FROM delivery
                    WHERE tenant_id = :tenantId AND status = :status
                      AND (CAST(:endpointId AS uuid) IS NULL OR endpoint_id = :endpointId)
                      AND (CAST(:since AS timestamptz) IS NULL OR created_at >= :since)
                    ORDER BY id LIMIT :max
                    FOR UPDATE SKIP LOCKED)
                """)
                .param("tenantId", tenantId)
                .param("status", status.name())
                .param("endpointId", endpointId)
                .param("since", since == null ? null : Timestamp.from(since))
                .param("max", max)
                .update();
    }

    private static Delivery map(ResultSet rs, int row) throws SQLException {
        Timestamp completed = rs.getTimestamp("completed_at");
        return new Delivery(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("endpoint_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("endpoint_url"),
                DeliveryStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                rs.getTimestamp("next_attempt_at").toInstant(),
                (Integer) rs.getObject("last_status_code"),
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                completed == null ? null : completed.toInstant());
    }
}
