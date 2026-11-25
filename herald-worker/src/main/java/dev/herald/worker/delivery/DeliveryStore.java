package dev.herald.worker.delivery;

import dev.herald.core.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * All delivery state transitions made by the worker. Every transition is a conditional UPDATE on
 * the expected current status, so duplicate queue messages, racing replicas and the sweeper can
 * never move a delivery backwards or deliver it twice concurrently.
 */
@Repository
public class DeliveryStore {

    public record Due(UUID deliveryId, UUID tenantId) {}

    private final JdbcClient jdbc;

    DeliveryStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Locks up to {@code batchSize} due deliveries, taking at most {@code perTenant} from any one
     * tenant. Must run inside the dispatch transaction: the row locks are what make concurrent
     * dispatchers safe ({@code SKIP LOCKED}).
     */
    public List<Due> lockDue(int perTenant, int batchSize) {
        return jdbc.sql("""
                SELECT id, tenant_id FROM delivery
                WHERE id IN (
                    SELECT id FROM (
                        SELECT id, row_number() OVER (PARTITION BY tenant_id ORDER BY next_attempt_at) AS rn
                        FROM delivery
                        WHERE status = 'PENDING' AND next_attempt_at <= now()
                    ) ranked
                    WHERE rn <= :perTenant
                    LIMIT :batchSize)
                  AND status = 'PENDING'
                FOR UPDATE SKIP LOCKED
                """)
                .param("perTenant", perTenant)
                .param("batchSize", batchSize)
                .query((rs, row) -> new Due(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class)))
                .list();
    }

    public void markQueued(List<UUID> ids) {
        jdbc.sql("UPDATE delivery SET status = 'QUEUED', updated_at = now() WHERE id = ANY(:ids) AND status = 'PENDING'")
                .param("ids", ids.toArray(UUID[]::new))
                .update();
    }

    /**
     * Atomically moves the delivery to DELIVERING and loads the job. Empty if another worker got
     * there first or the message is stale.
     *
     * <p>A due PENDING row is also claimable. SQS can hand the message to a consumer before the
     * dispatcher's transaction commits; the consumer's snapshot then still shows PENDING. Matching
     * on PENDING makes this UPDATE wait for the dispatcher's row lock and re-check the committed
     * (QUEUED) row, instead of silently skipping it. Requiring {@code next_attempt_at <= now()}
     * stops a stale duplicate message from bypassing a retry back-off.
     */
    public Optional<DeliveryJob> claim(UUID deliveryId) {
        return jdbc.sql("""
                WITH claimed AS (
                    UPDATE delivery SET status = 'DELIVERING', updated_at = now()
                    WHERE id = :id
                      AND (status = 'QUEUED' OR (status = 'PENDING' AND next_attempt_at <= now()))
                    RETURNING id, tenant_id, event_id, endpoint_id, attempt_count, attempts_in_cycle
                )
                SELECT c.id, c.tenant_id, c.event_id, c.endpoint_id, c.attempt_count, c.attempts_in_cycle,
                       e.type, e.payload::text AS payload, e.created_at AS event_created_at, e.trace_parent,
                       ep.url, ep.status AS endpoint_status, ep.secret_ciphertext, ep.previous_secret_ciphertext,
                       ep.previous_secret_expires_at, t.status AS tenant_status, t.max_concurrency
                FROM claimed c
                JOIN event e ON e.id = c.event_id
                JOIN endpoint ep ON ep.id = c.endpoint_id
                JOIN tenant t ON t.id = c.tenant_id
                """)
                .param("id", deliveryId)
                .query(DeliveryStore::mapJob)
                .optional();
    }

    /** Records an attempt and moves the delivery to its next state in one transaction. */
    @Transactional
    public void recordAttempt(DeliveryJob job, SendResult result, AttemptOutcome outcome, Instant nextAttemptAt) {
        int attemptNumber = job.attemptCount() + 1;
        jdbc.sql("""
                INSERT INTO delivery_attempt (id, delivery_id, tenant_id, attempt_number, outcome, status_code, error,
                                              response_excerpt, started_at, duration_ms)
                VALUES (:id, :deliveryId, :tenantId, :attempt, :outcome, :status, :error, :excerpt, :startedAt, :durationMs)
                """)
                .param("id", Ids.newId())
                .param("deliveryId", job.deliveryId())
                .param("tenantId", job.tenantId())
                .param("attempt", attemptNumber)
                .param("outcome", outcome.name())
                .param("status", result.statusCode())
                .param("error", result.error())
                .param("excerpt", result.responseExcerpt())
                .param("startedAt", Timestamp.from(result.startedAt()))
                .param("durationMs", (int) result.duration().toMillis())
                .update();

        String status = switch (outcome) {
            case SUCCEEDED -> "SUCCEEDED";
            case RETRYING -> "PENDING";
            case FAILED -> "DEAD";
        };
        jdbc.sql("""
                UPDATE delivery
                SET status = :status, attempt_count = :attempt, attempts_in_cycle = attempts_in_cycle + 1,
                    last_status_code = :code, last_error = :error,
                    next_attempt_at = COALESCE(CAST(:nextAttemptAt AS timestamptz), next_attempt_at),
                    completed_at = CASE WHEN CAST(:status AS text) IN ('SUCCEEDED', 'DEAD') THEN now() END,
                    updated_at = now()
                WHERE id = :id AND status = 'DELIVERING'
                """)
                .param("id", job.deliveryId())
                .param("status", status)
                .param("attempt", attemptNumber)
                .param("code", result.statusCode())
                .param("error", result.error())
                .param("nextAttemptAt", nextAttemptAt == null ? null : Timestamp.from(nextAttemptAt))
                .update();
    }

    /** Puts a claimed delivery back without spending an attempt (bulkhead full, circuit open, tenant suspended). */
    public void defer(UUID deliveryId, Instant until) {
        jdbc.sql("""
                UPDATE delivery SET status = 'PENDING', next_attempt_at = :until, updated_at = now()
                WHERE id = :id AND status = 'DELIVERING'
                """)
                .param("id", deliveryId)
                .param("until", Timestamp.from(until))
                .update();
    }

    /** Dead-letters a claimed delivery without an HTTP attempt (e.g. its endpoint was disabled). */
    public void abandon(UUID deliveryId, String reason) {
        jdbc.sql("""
                UPDATE delivery SET status = 'DEAD', last_error = :reason, completed_at = now(), updated_at = now()
                WHERE id = :id AND status = 'DELIVERING'
                """)
                .param("id", deliveryId)
                .param("reason", reason)
                .update();
    }

    /** Returns deliveries abandoned by a crashed worker or a lost queue message to PENDING. */
    public int releaseStale(Duration lease) {
        return jdbc.sql("""
                UPDATE delivery SET status = 'PENDING', next_attempt_at = now(), updated_at = now()
                WHERE status IN ('QUEUED', 'DELIVERING') AND updated_at < now() - make_interval(secs => :leaseSeconds)
                """)
                .param("leaseSeconds", lease.toSeconds())
                .update();
    }

    private static DeliveryJob mapJob(ResultSet rs, int row) throws SQLException {
        Timestamp previousExpiry = rs.getTimestamp("previous_secret_expires_at");
        return new DeliveryJob(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("endpoint_id", UUID.class),
                rs.getString("type"),
                rs.getString("payload"),
                rs.getTimestamp("event_created_at").toInstant(),
                rs.getString("trace_parent"),
                rs.getString("url"),
                "ENABLED".equals(rs.getString("endpoint_status")),
                rs.getBytes("secret_ciphertext"),
                rs.getBytes("previous_secret_ciphertext"),
                previousExpiry == null ? null : previousExpiry.toInstant(),
                "ACTIVE".equals(rs.getString("tenant_status")),
                rs.getInt("max_concurrency"),
                rs.getInt("attempt_count"),
                rs.getInt("attempts_in_cycle"));
    }
}
