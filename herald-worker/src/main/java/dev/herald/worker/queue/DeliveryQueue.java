package dev.herald.worker.queue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Work buffer between the dispatcher and the delivery consumers. */
public interface DeliveryQueue {

    record Message(UUID deliveryId, UUID tenantId) {}

    record Received(UUID deliveryId, String receipt) {}

    void send(List<Message> messages);

    List<Received> receive(int max, Duration wait);

    void acknowledge(Received message);
}
