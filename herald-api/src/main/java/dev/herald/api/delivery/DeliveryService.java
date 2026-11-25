package dev.herald.api.delivery;

import dev.herald.api.web.ConflictException;
import dev.herald.api.web.NotFoundException;
import dev.herald.api.web.Page;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeliveryService {

    /** Upper bound for a single bulk replay, to keep the transaction short. */
    static final int MAX_BULK_REPLAY = 10_000;

    private final DeliveryRepository repository;

    DeliveryService(DeliveryRepository repository) {
        this.repository = repository;
    }

    public record DeliveryDetail(Delivery delivery, List<DeliveryAttempt> attempts) {}

    public Page<Delivery> list(UUID tenantId, DeliveryStatus status, UUID endpointId, UUID before, int limit) {
        return Page.of(repository.findPage(tenantId, status, endpointId, before, limit + 1), limit, Delivery::id);
    }

    public DeliveryDetail get(UUID tenantId, UUID id) {
        Delivery delivery = repository.find(tenantId, id).orElseThrow(() -> new NotFoundException("Delivery", id));
        return new DeliveryDetail(delivery, repository.findAttempts(tenantId, id));
    }

    public Delivery replay(UUID tenantId, UUID id) {
        if (!repository.replay(tenantId, id)) {
            Delivery current = repository.find(tenantId, id).orElseThrow(() -> new NotFoundException("Delivery", id));
            throw new ConflictException("Delivery is " + current.status() + "; only SUCCEEDED or DEAD deliveries can be replayed");
        }
        return repository.find(tenantId, id).orElseThrow();
    }

    public int replayMatching(UUID tenantId, DeliveryStatus status, UUID endpointId, Instant since) {
        if (!DeliveryStatus.REPLAYABLE.contains(status)) {
            throw new ConflictException("Only SUCCEEDED or DEAD deliveries can be replayed");
        }
        return repository.replayMatching(tenantId, status, endpointId, since, MAX_BULK_REPLAY);
    }
}
