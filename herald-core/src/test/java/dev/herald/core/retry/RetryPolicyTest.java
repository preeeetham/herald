package dev.herald.core.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {


    private final RetryPolicy policy = new RetryPolicy(
            List.of(Duration.ofSeconds(5), Duration.ofSeconds(30)), 0.2, Duration.ofMinutes(10), fixed(0.5));

    @Test
    void followsScheduleAndThenGivesUp() {
        assertThat(policy.maxAttempts()).isEqualTo(3);
        assertThat(policy.nextDelay(1, Optional.empty())).contains(Duration.ofSeconds(5));
        assertThat(policy.nextDelay(2, Optional.empty())).contains(Duration.ofSeconds(30));
        assertThat(policy.nextDelay(3, Optional.empty())).isEmpty();
    }

    @Test
    void jitterStaysWithinRatio() {
        RetryPolicy low = new RetryPolicy(List.of(Duration.ofSeconds(10)), 0.2, Duration.ofMinutes(1), fixed(0.0));
        RetryPolicy high = new RetryPolicy(List.of(Duration.ofSeconds(10)), 0.2, Duration.ofMinutes(1), fixed(0.999999));

        assertThat(low.nextDelay(1, Optional.empty())).contains(Duration.ofSeconds(8));
        assertThat(high.nextDelay(1, Optional.empty()).orElseThrow()).isBetween(Duration.ofMillis(11_999), Duration.ofSeconds(12));
    }

    @Test
    void retryAfterCanExtendButNotShortenTheDelay() {
        assertThat(policy.nextDelay(1, Optional.of(Duration.ofSeconds(1)))).contains(Duration.ofSeconds(5));
        assertThat(policy.nextDelay(1, Optional.of(Duration.ofSeconds(60)))).contains(Duration.ofSeconds(60));
    }

    @Test
    void retryAfterIsCapped() {
        assertThat(policy.nextDelay(1, Optional.of(Duration.ofHours(5)))).contains(Duration.ofMinutes(10));
    }

    @Test
    void defaultScheduleSpansAboutElevenHours() {
        Duration total = RetryPolicy.DEFAULT_SCHEDULE.stream().reduce(Duration.ZERO, Duration::plus);

        assertThat(RetryPolicy.defaults().maxAttempts()).isEqualTo(9);
        assertThat(total).isBetween(Duration.ofHours(10), Duration.ofHours(11));
    }

    private static RandomGenerator fixed(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
