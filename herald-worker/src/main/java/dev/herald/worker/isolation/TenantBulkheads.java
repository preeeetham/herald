package dev.herald.worker.isolation;

import dev.herald.worker.config.WorkerProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

/**
 * Caps concurrent deliveries per tenant on this replica (isolation layer 4, ADR-0004). A tenant
 * whose endpoints are slow fills only its own permits; other tenants keep their full share of
 * worker capacity.
 */
@Component
public class TenantBulkheads {

    /** A held permit. Closing it releases exactly the semaphore it was taken from. */
    public interface Permit extends AutoCloseable {
        @Override
        void close();
    }

    private record Bulkhead(Semaphore permits, int size) {}

    private final Map<UUID, Bulkhead> bulkheads = new ConcurrentHashMap<>();
    private final int replicas;
    private final MeterRegistry meters;

    TenantBulkheads(WorkerProperties properties, MeterRegistry meters) {
        this.replicas = properties.isolation().replicas();
        this.meters = meters;
    }

    /** Non-blocking: empty when the tenant already uses all of its permits on this replica. */
    public Optional<Permit> tryAcquire(UUID tenantId, int tenantMaxConcurrency) {
        int size = Math.max(1, (int) Math.ceil((double) tenantMaxConcurrency / replicas));
        Bulkhead bulkhead = bulkheads.compute(tenantId, (id, current) -> {
            if (current == null) {
                Gauge.builder("herald.bulkhead.in_use", this, self -> self.inUse(id)).tag("tenant", id.toString()).register(meters);
            } else if (current.size() == size) {
                return current;
            }
            // First use, or the tenant's plan changed. Permits held on the old semaphore are
            // released back to it, so the resize never over-grants.
            return new Bulkhead(new Semaphore(size), size);
        });
        Semaphore semaphore = bulkhead.permits();
        return semaphore.tryAcquire() ? Optional.of(semaphore::release) : Optional.empty();
    }

    int inUse(UUID tenantId) {
        Bulkhead bulkhead = bulkheads.get(tenantId);
        return bulkhead == null ? 0 : bulkhead.size() - bulkhead.permits().availablePermits();
    }
}
