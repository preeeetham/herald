package dev.herald.api.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatsServiceTest {

    @Test
    void picksBucketsThatGiveAboutTwentyFourPoints() {
        assertThat(StatsService.bucketFor(Duration.ofHours(24))).isEqualTo(Duration.ofHours(1));
        assertThat(StatsService.bucketFor(Duration.ofHours(1))).isEqualTo(Duration.ofMinutes(3));
        assertThat(StatsService.bucketFor(Duration.ofMinutes(10))).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void fillsQuietBucketsWithZeros() {
        Instant since = Instant.parse("2026-11-25T00:00:00Z");
        var busy = new Stats.Bucket(since.plus(Duration.ofHours(1)), 5, 1, 120.0);

        List<Stats.Bucket> filled = StatsService.fillGaps(List.of(busy), since, since.plus(Duration.ofHours(3)), Duration.ofHours(1));

        assertThat(filled).hasSize(3);
        assertThat(filled.get(0).succeeded()).isZero();
        assertThat(filled.get(1)).isEqualTo(busy);
    }
}
