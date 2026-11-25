package dev.herald.api.stats;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

    /** Target number of points in the time series, whatever the window. */
    static final int SERIES_POINTS = 24;

    private final StatsRepository repository;
    private final Clock clock;

    StatsService(StatsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Stats forWindow(UUID tenantId, Duration window) {
        Duration bucket = bucketFor(window);
        Instant now = clock.instant();
        // Align the window start to the bucket size so buckets are stable between refreshes.
        long bucketSeconds = bucket.toSeconds();
        Instant since = Instant.ofEpochSecond(((now.minus(window).getEpochSecond()) / bucketSeconds) * bucketSeconds);

        var totals = repository.attemptTotals(tenantId, since);
        long failed = totals.total() - totals.succeeded();
        return new Stats(
                since,
                bucket,
                repository.countEvents(tenantId, since),
                repository.deliveriesByStatus(tenantId, since),
                new Stats.Attempts(totals.total(), totals.succeeded(), failed, totals.total() == 0 ? 1.0 : (double) totals.succeeded() / totals.total()),
                new Stats.Latency(totals.p50(), totals.p95(), totals.p99()),
                fillGaps(repository.series(tenantId, since, bucket), since, now, bucket),
                repository.endpointHealth(tenantId, since));
    }

    static Duration bucketFor(Duration window) {
        long seconds = Math.max(60, window.toSeconds() / SERIES_POINTS);
        long minutes = (long) Math.ceil(seconds / 60.0);
        return Duration.of(minutes, ChronoUnit.MINUTES);
    }

    /** Charts need a point for every bucket, including the quiet ones. */
    static List<Stats.Bucket> fillGaps(List<Stats.Bucket> buckets, Instant since, Instant now, Duration bucket) {
        Map<Instant, Stats.Bucket> byStart = buckets.stream().collect(Collectors.toMap(Stats.Bucket::start, Function.identity()));
        List<Stats.Bucket> filled = new ArrayList<>();
        for (Instant start = since; start.isBefore(now); start = start.plus(bucket)) {
            filled.add(byStart.getOrDefault(start, new Stats.Bucket(start, 0, 0, null)));
        }
        return filled;
    }
}
