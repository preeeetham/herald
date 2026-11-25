package dev.herald.api.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Distributed per-tenant token bucket backed by a single atomic Lua script, so every API replica
 * shares one bucket per tenant with no race between read and write.
 *
 * <p>Fails open: if Redis is unavailable, requests are allowed and the failure is counted. For an
 * ingestion service, briefly losing rate limiting is preferable to rejecting every event.
 */
@Component
public class RateLimiter {

    public record Decision(boolean allowed, long limit, long remaining, Duration retryAfter) {}

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT = RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);

    private final StringRedisTemplate redis;
    private final MeterRegistry meters;

    RateLimiter(StringRedisTemplate redis, MeterRegistry meters) {
        this.redis = redis;
        this.meters = meters;
    }

    public Decision tryConsume(UUID tenantId, int ratePerSecond, int burst) {
        try {
            List<?> result = redis.execute(
                    SCRIPT, List.of("herald:ratelimit:" + tenantId), Integer.toString(ratePerSecond), Integer.toString(burst), "1");
            boolean allowed = ((Number) result.get(0)).longValue() == 1;
            long remaining = ((Number) result.get(1)).longValue();
            Duration retryAfter = Duration.ofMillis(((Number) result.get(2)).longValue());
            if (!allowed) {
                meters.counter("herald.ratelimit.rejected", "tenant", tenantId.toString()).increment();
            }
            return new Decision(allowed, burst, remaining, retryAfter);
        } catch (RuntimeException e) {
            meters.counter("herald.ratelimit.errors").increment();
            log.warn("Rate limiter unavailable, failing open: {}", e.getMessage());
            return new Decision(true, burst, burst, Duration.ZERO);
        }
    }
}
