package dev.herald.worker.delivery;

import dev.herald.core.retry.RetryPolicy;
import dev.herald.worker.config.WorkerProperties;
import dev.herald.worker.isolation.EndpointCircuitBreakers;
import dev.herald.worker.isolation.TenantBulkheads;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
    private final Tracer tracer;
    private final Propagator propagator;

    DeliveryProcessor(
            DeliveryStore store,
            WebhookSender sender,
            RetryPolicy retryPolicy,
            TenantBulkheads bulkheads,
            EndpointCircuitBreakers circuitBreakers,
            MeterRegistry meters,
            Clock clock,
            Tracer tracer,
            Propagator propagator,
            WorkerProperties properties) {
        this.tracer = tracer;
        this.propagator = propagator;
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
            SendResult result = sendTraced(job);
            circuitBreakers.record(job.endpointId(), result);
            apply(job, result);
        }
    }

    /**
     * Continues the trace started when the event was published, so one trace shows publish → queue
     * → delivery → receiver. The sender propagates this span's context to the receiver.
     */
    private SendResult sendTraced(DeliveryJob job) {
        Span span = (job.traceParent() == null
                        ? tracer.spanBuilder()
                        : propagator.extract(Map.of("traceparent", job.traceParent()), Map::get))
                .name("herald.deliver")
                .kind(Span.Kind.CLIENT)
                .tag("herald.tenant_id", job.tenantId().toString())
                .tag("herald.delivery_id", job.deliveryId().toString())
                .tag("herald.event_type", job.eventType())
                .tag("herald.attempt", Integer.toString(job.attemptCount() + 1))
                .start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            SendResult result = sender.send(job);
            if (result.statusCode() != null) {
                span.tag("http.response.status_code", Integer.toString(result.statusCode()));
            }
            if (!result.succeeded()) {
                span.error(new IllegalStateException(result.error()));
            }
            return result;
        } finally {
            span.end();
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
