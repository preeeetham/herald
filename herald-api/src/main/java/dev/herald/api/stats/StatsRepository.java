package dev.herald.api.stats;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Aggregations over {@code delivery_attempt} (indexed by tenant and start time). */
@Repository
class StatsRepository {

    private final JdbcClient jdbc;

    StatsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long countEvents(UUID tenantId, Instant since) {
        return jdbc.sql("SELECT count(*) FROM event WHERE tenant_id = :t AND created_at >= :since")
                .param("t", tenantId)
                .param("since", Timestamp.from(since))
                .query(Long.class)
                .single();
    }

    Map<String, Long> deliveriesByStatus(UUID tenantId, Instant since) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : List.of("PENDING", "QUEUED", "DELIVERING", "SUCCEEDED", "DEAD")) {
            counts.put(status, 0L);
        }
        jdbc.sql("SELECT status, count(*) AS n FROM delivery WHERE tenant_id = :t AND created_at >= :since GROUP BY status")
                .param("t", tenantId)
                .param("since", Timestamp.from(since))
                .query((rs, row) -> counts.put(rs.getString("status"), rs.getLong("n")))
                .list();
        return counts;
    }

    record AttemptTotals(long total, long succeeded, Double p50, Double p95, Double p99) {}

    AttemptTotals attemptTotals(UUID tenantId, Instant since) {
        return jdbc.sql("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE outcome = 'SUCCEEDED') AS succeeded,
                       percentile_cont(0.50) WITHIN GROUP (ORDER BY duration_ms) AS p50,
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95,
                       percentile_cont(0.99) WITHIN GROUP (ORDER BY duration_ms) AS p99
                FROM delivery_attempt WHERE tenant_id = :t AND started_at >= :since
                """)
                .param("t", tenantId)
                .param("since", Timestamp.from(since))
                .query((rs, row) -> new AttemptTotals(
                        rs.getLong("total"), rs.getLong("succeeded"),
                        (Double) rs.getObject("p50"), (Double) rs.getObject("p95"), (Double) rs.getObject("p99")))
                .single();
    }

    List<Stats.Bucket> series(UUID tenantId, Instant since, Duration bucket) {
        return jdbc.sql("""
                SELECT date_bin(make_interval(secs => :bucketSeconds), started_at, :since) AS bucket_start,
                       count(*) FILTER (WHERE outcome = 'SUCCEEDED') AS succeeded,
                       count(*) FILTER (WHERE outcome <> 'SUCCEEDED') AS failed,
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95
                FROM delivery_attempt WHERE tenant_id = :t AND started_at >= :since
                GROUP BY 1 ORDER BY 1
                """)
                .param("t", tenantId)
                .param("since", Timestamp.from(since))
                .param("bucketSeconds", bucket.toSeconds())
                .query((rs, row) -> new Stats.Bucket(
                        rs.getTimestamp("bucket_start").toInstant(), rs.getLong("succeeded"), rs.getLong("failed"), (Double) rs.getObject("p95")))
                .list();
    }

    List<Stats.EndpointHealth> endpointHealth(UUID tenantId, Instant since) {
        return jdbc.sql("""
                SELECT ep.id, ep.url,
                       count(a.id) AS attempts,
                       count(a.id) FILTER (WHERE a.outcome = 'SUCCEEDED') AS succeeded,
                       (SELECT count(*) FROM delivery dd WHERE dd.endpoint_id = ep.id AND dd.status = 'DEAD') AS dead,
                       max(a.started_at) AS last_attempt
                FROM endpoint ep
                LEFT JOIN delivery d ON d.endpoint_id = ep.id
                LEFT JOIN delivery_attempt a ON a.delivery_id = d.id AND a.started_at >= :since
                WHERE ep.tenant_id = :t
                GROUP BY ep.id, ep.url
                ORDER BY ep.id
                """)
                .param("t", tenantId)
                .param("since", Timestamp.from(since))
                .query((rs, row) -> {
                    long attempts = rs.getLong("attempts");
                    Timestamp last = rs.getTimestamp("last_attempt");
                    return new Stats.EndpointHealth(
                            rs.getObject("id", UUID.class),
                            rs.getString("url"),
                            attempts,
                            attempts == 0 ? 1.0 : (double) rs.getLong("succeeded") / attempts,
                            rs.getLong("dead"),
                            last == null ? null : last.toInstant());
                })
                .list();
    }
}
