package dev.herald.worker.queue;

import dev.herald.worker.config.WorkerProperties;
import dev.herald.worker.delivery.DeliveryProcessor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Long-polls SQS and processes each message on its own virtual thread. A global semaphore bounds
 * in-flight deliveries per replica; on shutdown polling stops first and in-flight deliveries are
 * drained before the context closes (graceful rolling deploys).
 */
@Component
class QueueConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(QueueConsumer.class);
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(20);

    private final DeliveryQueue queue;
    private final DeliveryProcessor processor;
    private final WorkerProperties properties;
    private final Semaphore inFlight;
    private final List<Thread> pollers = new ArrayList<>();
    private volatile boolean running;
    private ExecutorService deliveries;

    QueueConsumer(DeliveryQueue queue, DeliveryProcessor processor, WorkerProperties properties) {
        this.queue = queue;
        this.processor = processor;
        this.properties = properties;
        this.inFlight = new Semaphore(properties.delivery().maxInFlight());
    }

    @Override
    public void start() {
        running = true;
        deliveries = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("herald-delivery-", 0).factory());
        for (int i = 0; i < properties.queue().consumers(); i++) {
            pollers.add(Thread.ofVirtual().name("herald-poller-" + i).start(this::poll));
        }
        log.info("Started {} queue pollers, max {} in-flight deliveries", pollers.size(), properties.delivery().maxInFlight());
    }

    private void poll() {
        while (running) {
            try {
                for (DeliveryQueue.Received message : queue.receive(10, properties.queue().waitTime())) {
                    inFlight.acquire();
                    deliveries.submit(() -> handle(message));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                if (running) {
                    log.warn("Queue receive failed; backing off", e);
                    sleepQuietly(Duration.ofSeconds(1));
                }
            }
        }
    }

    private void handle(DeliveryQueue.Received message) {
        try {
            processor.process(message.deliveryId());
            queue.acknowledge(message);
        } catch (RuntimeException e) {
            // Not acknowledged: SQS redelivers after the visibility timeout; the sweeper is the backstop.
            log.error("Processing delivery {} failed", message.deliveryId(), e);
        } finally {
            inFlight.release();
        }
    }

    @Override
    public void stop() {
        running = false;
        pollers.forEach(Thread::interrupt);
        pollers.clear();
        deliveries.shutdown();
        try {
            if (!deliveries.awaitTermination(DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("In-flight deliveries did not finish within {}; the sweeper will recover them", DRAIN_TIMEOUT);
                deliveries.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
