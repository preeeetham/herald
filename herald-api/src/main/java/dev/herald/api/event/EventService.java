package dev.herald.api.event;

import dev.herald.api.config.HeraldProperties;
import dev.herald.api.delivery.DeliveryRepository;
import dev.herald.api.endpoint.Endpoint;
import dev.herald.api.endpoint.EndpointRepository;
import dev.herald.api.web.BadRequestException;
import dev.herald.api.web.ConflictException;
import dev.herald.api.web.NotFoundException;
import dev.herald.api.web.Page;
import dev.herald.api.web.PayloadTooLargeException;
import dev.herald.core.event.EventTypes;
import dev.herald.core.id.Ids;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishing is a transactional outbox: the event and one PENDING delivery per subscribed endpoint
 * are committed atomically. No broker is called in the request path, so an acknowledged event can
 * never be lost between "saved" and "sent".
 */
@Service
public class EventService {

    private final EventRepository events;
    private final EndpointRepository endpoints;
    private final DeliveryRepository deliveries;
    private final MeterRegistry meters;
    private final int maxPayloadBytes;

    EventService(
            EventRepository events, EndpointRepository endpoints, DeliveryRepository deliveries, MeterRegistry meters, HeraldProperties properties) {
        this.events = events;
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.meters = meters;
        this.maxPayloadBytes = properties.events().maxPayloadBytes();
    }

    /** {@code replayed} is true when an earlier request with the same idempotency key is returned. */
    public record Published(Event event, int deliveryCount, boolean replayed) {}

    @Transactional
    public Published publish(UUID tenantId, String type, String payloadJson, String idempotencyKey, String traceParent) {
        if (!EventTypes.isValidType(type)) {
            throw new BadRequestException("Invalid event type '" + type + "': use dot-separated lowercase segments, e.g. claim.created");
        }
        if (payloadJson.getBytes(StandardCharsets.UTF_8).length > maxPayloadBytes) {
            throw new PayloadTooLargeException(maxPayloadBytes);
        }
        if (idempotencyKey != null) {
            Optional<Published> previous = findPrevious(tenantId, idempotencyKey, type, payloadJson);
            if (previous.isPresent()) {
                return previous.get();
            }
        }

        Event event = new Event(Ids.newId(), tenantId, type, payloadJson, idempotencyKey, Instant.now());
        if (!events.insertIfAbsent(event, traceParent)) {
            // Lost a race with a concurrent request using the same key; its row is now committed.
            return findPrevious(tenantId, idempotencyKey, type, payloadJson).orElseThrow();
        }

        List<UUID> targets = endpoints.findEnabledByTenant(tenantId).stream()
                .filter(endpoint -> endpoint.subscribesTo(type))
                .map(Endpoint::id)
                .toList();
        deliveries.createPending(tenantId, event.id(), targets);
        meters.counter("herald.events.ingested", "tenant", tenantId.toString()).increment();
        meters.counter("herald.deliveries.created", "tenant", tenantId.toString()).increment(targets.size());
        return new Published(event, targets.size(), false);
    }

    public Event get(UUID tenantId, UUID id) {
        return events.find(tenantId, id).orElseThrow(() -> new NotFoundException("Event", id));
    }

    public Page<Event> list(UUID tenantId, String type, UUID before, int limit) {
        return Page.of(events.findPage(tenantId, type, before, limit + 1), limit, Event::id);
    }

    private Optional<Published> findPrevious(UUID tenantId, String key, String type, String payloadJson) {
        return events.findByIdempotencyKey(tenantId, key).map(existing -> {
            if (!events.samePayload(existing.id(), type, payloadJson)) {
                throw new ConflictException("Idempotency-Key '" + key + "' was already used with a different request");
            }
            return new Published(existing, deliveries.countByEvent(existing.id()), true);
        });
    }
}
