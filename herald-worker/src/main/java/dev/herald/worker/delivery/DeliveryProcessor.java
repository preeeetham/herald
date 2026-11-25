package dev.herald.worker.delivery;

import dev.herald.core.retry.RetryPolicy;
import dev.herald.worker.config.WorkerProperties;
import dev.herald.worker.isolation.EndpointCircuitBreakers;
import dev.herald.worker.isolation.TenantBulkheads;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Processes one queued delivery: claim → isolation checks → signed POST → state transition.
 *
 * <p>Deferrals (bulkhead full, circuit open, tenant suspended) put the delivery back as PENDING
 * without spending an attempt, so protective measures never push a delivery towards the DLQ.
 */
@Service
public class DeliveryProcessor {

    private static final Logger log = LoggerFactory.getLogger(DeliveryProcessor.class);
    private static final Duration SUSPENDED_TENANT_DEFERRAL = Duration.ofMinutes(1);

    private final DeliveryStore store;
    private final WebhookSender sender;
    private final RetryPolicy retryPolicy;
    private final TenantBulkheads bulkheads;
    private final EndpointCircuitBreakers circuitBreakers;
    private final MeterRegistry meters;
    private final Clock clock;
    private final Duration bulkheadDeferral;

    DeliveryProcessor(
            DeliveryStore store,
            WebhookSender sender,
            RetryPolicy retryPolicy,
            TenantBulkheads bulkheads,
            EndpointCircuitBreakers circuitBreakers,
            MeterRegistry meters,
            Clock clock,
            WorkerProperties properties) {
        this.store = store;
        this.sender = sender;
        this.retryPolicy = retryPolicy;
        this.bulkheads = bulkheads;
        this.circuitBreakers = circuitBreakers;
        this.meters = meters;
        this.clock = clock;
        this.bulkheadDeferral = properties.isolation().bulkheadDeferral();
    }

    public void process(UUID deliveryId) {
        Optional<DeliveryJob> claimed = store.claim(deliveryId);
        if (claimed.isEmpty()) {
            meters.counter("herald.delivery.duplicates").increment(); // duplicate or stale queue message
            return;
        }
        DeliveryJob job = claimed.get();
        if (!job.endpointEnabled()) {
            store.abandon(job.deliveryId(), "Endpoint disabled");
            return;
        }
        if (!job.tenantActive()) {
            defer(job, SUSPENDED_TENANT_DEFERRAL, "tenant_suspended");
            return;
        }
        Optional<TenantBulkheads.Permit> permit = bulkheads.tryAcquire(job.tenantId(), job.tenantMaxConcurrency());
        if (permit.isEmpty()) {
            defer(job, bulkheadDeferral, "bulkhead");
            return;
        }
        try (TenantBulkheads.Permit held = permit.get()) {
            if (!circuitBreakers.tryAcquire(job.endpointId())) {
                defer(job, circuitBreakers.openDuration(), "circuit_open");
                return;
            }
            SendResult result = sender.send(job);
            circuitBreakers.record(job.endpointId(), result);
            apply(job, result);
        }
    }

    private void apply(DeliveryJob job, SendResult result) {
        AttemptOutcome outcome;
        Instant nextAttemptAt = null;
        if (result.succeeded()) {
            outcome = AttemptOutcome.SUCCEEDED;
        } else if (result.permanent()) {
            outcome = AttemptOutcome.FAILED;
        } else {
            Optional<Duration> delay = retryPolicy.nextDelay(job.nextAttemptInCycle(), result.retryAfter());
            outcome = delay.isPresent() ? AttemptOutcome.RETRYING : AttemptOutcome.FAILED;
            nextAttemptAt = delay.map(d -> clock.instant().plus(d)).orElse(null);
        }
        store.recordAttempt(job, result, outcome, nextAttemptAt);

        String tenant = job.tenantId().toString();
        meters.counter("herald.delivery.attempts", "tenant", tenant, "outcome", outcome.name().toLowerCase()).increment();
        Timer.builder("herald.delivery.duration")
                .tag("outcome", outcome.name().toLowerCase())
                .publishPercentileHistogram()
                .register(meters)
                .record(result.duration());
        log.atInfo()
                .setMessage("Delivery attempt finished")
                .addKeyValue("delivery_id", job.deliveryId())
                .addKeyValue("tenant_id", tenant)
                .addKeyValue("endpoint_id", job.endpointId())
                .addKeyValue("attempt", job.attemptCount() + 1)
                .addKeyValue("outcome", outcome)
                .addKeyValue("status_code", result.statusCode())
                .addKeyValue("duration_ms", result.duration().toMillis())
                .addKeyValue("error", result.error())
                .log();
    }

    private void defer(DeliveryJob job, Duration delay, String reason) {
        store.defer(job.deliveryId(), clock.instant().plus(delay));
        meters.counter("herald.delivery.deferrals", "tenant", job.tenantId().toString(), "reason", reason).increment();
    }
}
