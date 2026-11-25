package dev.herald.worker.dispatch;

import dev.herald.worker.config.WorkerProperties;
import dev.herald.worker.delivery.DeliveryStore;
import dev.herald.worker.queue.DeliveryQueue;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves due deliveries from Postgres to SQS (the outbox relay).
 *
 * <p>Rows are locked with {@code FOR UPDATE SKIP LOCKED}, sent, and only then marked QUEUED in the
 * same transaction. If SQS fails, the transaction rolls back and the rows stay PENDING. If the
 * commit fails after a successful send, the duplicate message is harmlessly dropped by the
 * conditional claim.
 */
@Component
class Dispatcher {

    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);
    /** Bound the work done in one tick so a backlog cannot starve the scheduler thread. */
    private static final int MAX_BATCHES_PER_TICK = 20;

    private final DeliveryStore store;
    private final DeliveryQueue queue;
    private final TransactionTemplate transactions;
    private final WorkerProperties.Dispatch config;
    private final DistributionSummary batchSizes;

    Dispatcher(DeliveryStore store, DeliveryQueue queue, TransactionTemplate transactions, WorkerProperties properties, MeterRegistry meters) {
        this.store = store;
        this.queue = queue;
        this.transactions = transactions;
        this.config = properties.dispatch();
        this.batchSizes = DistributionSummary.builder("herald.dispatch.batch_size").register(meters);
    }

    @Scheduled(fixedDelayString = "${herald.dispatch.interval}")
    void tick() {
        try {
            for (int i = 0; i < MAX_BATCHES_PER_TICK; i++) {
                int dispatched = dispatchBatch();
                if (dispatched < config.batchSize()) {
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Dispatch failed; rows remain PENDING and will be retried", e);
        }
    }

    int dispatchBatch() {
        Integer count = transactions.execute(status -> {
            List<DeliveryStore.Due> due = store.lockDue(config.perTenantLimit(), config.batchSize());
            if (due.isEmpty()) {
                return 0;
            }
            queue.send(due.stream().map(d -> new DeliveryQueue.Message(d.deliveryId(), d.tenantId())).toList());
            store.markQueued(due.stream().map(DeliveryStore.Due::deliveryId).toList());
            return due.size();
        });
        int dispatched = count == null ? 0 : count;
        if (dispatched > 0) {
            batchSizes.record(dispatched);
        }
        return dispatched;
    }
}
