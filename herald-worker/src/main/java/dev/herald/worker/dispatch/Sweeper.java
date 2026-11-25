package dev.herald.worker.dispatch;

import dev.herald.worker.config.WorkerProperties;
import dev.herald.worker.delivery.DeliveryStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Recovers deliveries left QUEUED or DELIVERING by a crashed worker or a lost queue message. */
@Component
class Sweeper {

    private static final Logger log = LoggerFactory.getLogger(Sweeper.class);

    private final DeliveryStore store;
    private final Duration lease;
    private final Counter released;

    Sweeper(DeliveryStore store, WorkerProperties properties, MeterRegistry meters) {
        this.store = store;
        this.lease = properties.sweeper().lease();
        this.released = meters.counter("herald.sweeper.released");
    }

    @Scheduled(fixedDelayString = "${herald.sweeper.interval}")
    void sweep() {
        try {
            int count = store.releaseStale(lease);
            if (count > 0) {
                released.increment(count);
                log.warn("Released {} stale deliveries back to PENDING", count);
            }
        } catch (RuntimeException e) {
            log.warn("Sweep failed", e);
        }
    }
}
