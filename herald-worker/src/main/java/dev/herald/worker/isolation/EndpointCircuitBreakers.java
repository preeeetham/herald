package dev.herald.worker.isolation;

import dev.herald.worker.config.WorkerProperties;
import dev.herald.worker.delivery.SendResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * One circuit breaker per endpoint. When an endpoint keeps failing, its deliveries are deferred
 * without making HTTP calls, so a dead receiver stops consuming worker time and its retry budget
 * is not burnt while it is down. Half-open probes detect recovery.
 *
 * <p>Only transient failures count against the circuit: a 4xx means the request was rejected, not
 * that the receiver is unhealthy.
 */
@Component
public class EndpointCircuitBreakers {

    private final CircuitBreakerRegistry registry;
    private final Duration openDuration;

    EndpointCircuitBreakers(WorkerProperties properties, MeterRegistry meters) {
        var isolation = properties.isolation();
        this.openDuration = isolation.circuitOpenDuration();
        this.registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(isolation.circuitWindowSize())
                .minimumNumberOfCalls(isolation.circuitMinimumCalls())
                .failureRateThreshold(isolation.circuitFailureRatePercent())
                .waitDurationInOpenState(openDuration)
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .build());
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
    }

    /** {@code true} if a call may be made now; otherwise the caller should defer. */
    public boolean tryAcquire(UUID endpointId) {
        return breaker(endpointId).tryAcquirePermission();
    }

    public Duration openDuration() {
        return openDuration;
    }

    public void record(UUID endpointId, SendResult result) {
        CircuitBreaker breaker = breaker(endpointId);
        long nanos = result.duration().toNanos();
        if (result.succeeded()) {
            breaker.onSuccess(nanos, TimeUnit.NANOSECONDS);
        } else if (result.permanent()) {
            breaker.releasePermission();
        } else {
            breaker.onError(nanos, TimeUnit.NANOSECONDS, new ReceiverUnavailable(result.error()));
        }
    }

    public CircuitBreaker.State state(UUID endpointId) {
        return breaker(endpointId).getState();
    }

    private CircuitBreaker breaker(UUID endpointId) {
        return registry.circuitBreaker(endpointId.toString());
    }

    /** Marker for transient receiver failures; carries no stack trace. */
    private static final class ReceiverUnavailable extends RuntimeException {
        ReceiverUnavailable(String message) {
            super(message, null, false, false);
        }
    }
}
