package dev.herald.core.retry;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Exponential back-off schedule with proportional jitter.
 *
 * <p>Attempt {@code n} (1-based) that fails is followed by {@code schedule[n-1]} ± jitter. Once
 * the schedule is exhausted the delivery is dead-lettered. A receiver-supplied {@code Retry-After}
 * can lengthen — never shorten — the wait, up to {@code maxRetryAfter}.
 */
public final class RetryPolicy {

    public static final List<Duration> DEFAULT_SCHEDULE = List.of(
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(3),
            Duration.ofHours(6));

    private final List<Duration> schedule;
    private final double jitterRatio;
    private final Duration maxRetryAfter;
    private final RandomGenerator random;

    public RetryPolicy(List<Duration> schedule, double jitterRatio, Duration maxRetryAfter, RandomGenerator random) {
        if (schedule.isEmpty()) {
            throw new IllegalArgumentException("Retry schedule must not be empty");
        }
        if (jitterRatio < 0 || jitterRatio >= 1) {
            throw new IllegalArgumentException("Jitter ratio must be in [0, 1)");
        }
        this.schedule = List.copyOf(schedule);
        this.jitterRatio = jitterRatio;
        this.maxRetryAfter = maxRetryAfter;
        this.random = random;
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(DEFAULT_SCHEDULE, 0.2, Duration.ofHours(1), RandomGenerator.getDefault());
    }

    /** Total attempts including the first one. */
    public int maxAttempts() {
        return schedule.size() + 1;
    }

    /**
     * Delay before the next attempt, or empty when {@code failedAttempt} was the last one allowed.
     *
     * @param failedAttempt the 1-based number of the attempt that just failed
     * @param retryAfter optional delay requested by the receiver
     */
    public Optional<Duration> nextDelay(int failedAttempt, Optional<Duration> retryAfter) {
        if (failedAttempt < 1) {
            throw new IllegalArgumentException("Attempt numbers are 1-based");
        }
        if (failedAttempt >= maxAttempts()) {
            return Optional.empty();
        }
        Duration base = schedule.get(failedAttempt - 1);
        long baseMillis = base.toMillis();
        long jitter = (long) (baseMillis * jitterRatio * (random.nextDouble() * 2 - 1));
        Duration delay = Duration.ofMillis(Math.max(0, baseMillis + jitter));
        if (retryAfter.isPresent()) {
            Duration requested = retryAfter.get().compareTo(maxRetryAfter) > 0 ? maxRetryAfter : retryAfter.get();
            if (requested.compareTo(delay) > 0) {
                delay = requested;
            }
        }
        return Optional.of(delay);
    }
}
