package dev.herald.api.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Delivery health of one tenant over a time window. */
public record Stats(
        Instant since,
        Duration bucket,
        long events,
        Map<String, Long> deliveriesByStatus,
        Attempts attempts,
        Latency latencyMs,
        List<Bucket> series,
        List<EndpointHealth> endpoints) {

    public record Attempts(long total, long succeeded, long failed, double successRate) {}

    public record Latency(Double p50, Double p95, Double p99) {}

    public record Bucket(Instant start, long succeeded, long failed, Double p95Ms) {}

    public record EndpointHealth(UUID id, String url, long attempts, double successRate, long dead, Instant lastAttemptAt) {}
}
