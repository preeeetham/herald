package dev.herald.api.delivery;

import dev.herald.api.security.TenantPrincipal;
import dev.herald.api.web.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/v1/deliveries")
@Tag(name = "Deliveries")
@SecurityRequirement(name = "apiKey")
class DeliveryController {

    private final DeliveryService service;

    DeliveryController(DeliveryService service) {
        this.service = service;
    }

    record DeliveryDetailResponse(Delivery delivery, List<DeliveryAttempt> attempts) {}

    record BulkReplayRequest(@NotNull DeliveryStatus status, UUID endpointId, Instant since) {}

    record BulkReplayResponse(int replayed) {}

    @GetMapping
    @Operation(summary = "List deliveries", description = "Newest first. Filter by status (e.g. DEAD) or endpoint.")
    Page<Delivery> list(
            @AuthenticationPrincipal TenantPrincipal tenant,
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) UUID endpointId,
            @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return service.list(tenant.tenantId(), status, endpointId, before, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a delivery with every attempt")
    DeliveryDetailResponse get(@AuthenticationPrincipal TenantPrincipal tenant, @PathVariable UUID id) {
        var detail = service.get(tenant.tenantId(), id);
        return new DeliveryDetailResponse(detail.delivery(), detail.attempts());
    }

    @PostMapping("/{id}/replay")
    @Operation(summary = "Replay a delivery", description = "Re-queues a SUCCEEDED or DEAD delivery with a fresh retry budget.")
    Delivery replay(@AuthenticationPrincipal TenantPrincipal tenant, @PathVariable UUID id) {
        return service.replay(tenant.tenantId(), id);
    }

    @PostMapping("/replay")
    @Operation(summary = "Bulk replay", description = "Re-queues up to 10,000 matching deliveries, e.g. all DEAD for an endpoint after an outage.")
    BulkReplayResponse replayMatching(@AuthenticationPrincipal TenantPrincipal tenant, @Valid @RequestBody BulkReplayRequest request) {
        return new BulkReplayResponse(service.replayMatching(tenant.tenantId(), request.status(), request.endpointId(), request.since()));
    }
}
