package dev.herald.api.event;

import dev.herald.api.delivery.Delivery;
import dev.herald.api.delivery.DeliveryRepository;
import dev.herald.api.security.TenantPrincipal;
import dev.herald.api.web.BadRequestException;
import dev.herald.api.web.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@Validated
@RequestMapping("/v1/events")
@Tag(name = "Events")
@SecurityRequirement(name = "apiKey")
class EventController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final EventService service;
    private final DeliveryRepository deliveries;
    private final JsonMapper json;

    EventController(EventService service, DeliveryRepository deliveries, JsonMapper json) {
        this.service = service;
        this.deliveries = deliveries;
        this.json = json;
    }

    record PublishRequest(@NotBlank @Size(max = 128) String type, @NotNull JsonNode payload) {}

    record PublishResponse(UUID id, String type, int deliveries, Instant createdAt) {}

    record EventResponse(UUID id, String type, JsonNode payload, Instant createdAt) {}

    record EventDetailResponse(UUID id, String type, JsonNode payload, Instant createdAt, List<Delivery> deliveries) {}

    @PostMapping
    @Operation(
            summary = "Publish an event",
            description = "Atomically stores the event and fans it out to every subscribed endpoint. Returns 202, or 200 with "
                    + "Idempotent-Replayed: true when the Idempotency-Key was seen before.")
    ResponseEntity<PublishResponse> publish(
            @AuthenticationPrincipal TenantPrincipal tenant,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(min = 1, max = 255) String idempotencyKey,
            @RequestHeader(name = "traceparent", required = false) String traceParent,
            @Valid @RequestBody PublishRequest request) {
        if (!request.payload().isObject()) {
            throw new BadRequestException("payload must be a JSON object");
        }
        var published = service.publish(
                tenant.tenantId(), request.type(), json.writeValueAsString(request.payload()), idempotencyKey, traceParent);
        var body = new PublishResponse(
                published.event().id(), published.event().type(), published.deliveryCount(), published.event().createdAt());
        return published.replayed()
                ? ResponseEntity.ok().header(IDEMPOTENT_REPLAYED, "true").body(body)
                : ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping
    @Operation(summary = "List events", description = "Newest first; paginate with ?before=<nextCursor>.")
    Page<EventResponse> list(
            @AuthenticationPrincipal TenantPrincipal tenant,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return service.list(tenant.tenantId(), type, before, limit)
                .map(e -> new EventResponse(e.id(), e.type(), json.readTree(e.payload()), e.createdAt()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an event and the status of each of its deliveries")
    EventDetailResponse get(@AuthenticationPrincipal TenantPrincipal tenant, @PathVariable UUID id) {
        Event event = service.get(tenant.tenantId(), id);
        return new EventDetailResponse(
                event.id(), event.type(), json.readTree(event.payload()), event.createdAt(), deliveries.findByEvent(tenant.tenantId(), id));
    }
}
