package dev.herald.api.endpoint;

import dev.herald.api.security.TenantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/endpoints")
@Tag(name = "Endpoints")
@SecurityRequirement(name = "apiKey")
class EndpointController {

    private final EndpointService service;

    EndpointController(EndpointService service) {
        this.service = service;
    }

    record CreateEndpointRequest(
            @NotBlank @Size(max = 2048) String url,
            @Size(max = 500) String description,
            @NotEmpty @Size(max = 50) List<String> eventTypes) {}

    record UpdateEndpointRequest(
            @Size(min = 1, max = 2048) String url,
            @Size(max = 500) String description,
            @Size(min = 1, max = 50) List<String> eventTypes,
            EndpointStatus status) {}

    record EndpointResponse(
            UUID id, String url, String description, List<String> eventTypes, EndpointStatus status, Instant createdAt, Instant updatedAt) {
        static EndpointResponse from(Endpoint e) {
            return new EndpointResponse(e.id(), e.url(), e.description(), e.eventTypes(), e.status(), e.createdAt(), e.updatedAt());
        }
    }

    record EndpointWithSecretResponse(EndpointResponse endpoint, String secret) {
        static EndpointWithSecretResponse from(EndpointService.WithSecret w) {
            return new EndpointWithSecretResponse(EndpointResponse.from(w.endpoint()), w.secret());
        }
    }

    record SecretResponse(String secret) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an endpoint", description = "Returns the signing secret used to verify deliveries.")
    EndpointWithSecretResponse create(@AuthenticationPrincipal TenantPrincipal tenant, @Valid @RequestBody CreateEndpointRequest request) {
        return EndpointWithSecretResponse.from(service.create(tenant.tenantId(), request.url(), request.description(), request.eventTypes()));
    }

    @GetMapping
    @Operation(summary = "List endpoints")
    List<EndpointResponse> list(@AuthenticationPrincipal TenantPrincipal tenant) {
        return service.list(tenant.tenantId()).stream().map(EndpointResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an endpoint")
    EndpointResponse get(@AuthenticationPrincipal TenantPrincipal tenant, @PathVariable UUID id) {
        return EndpointResponse.from(service.get(tenant.tenantId(), id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an endpoint", description = "Change URL, subscriptions, or enable / disable it.")
    EndpointResponse update(
            @AuthenticationPrincipal TenantPrincipal tenant, @PathVariable UUID id, @Valid @RequestBody UpdateEndpointRequest request) {
        return EndpointResponse.from(
                service.update(tenant.tenantId(), id, request.url(), request.description(), request.eventTypes(), request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an endpoint")
    void delete(@AuthenticationPrincipal TenantPrincipal tenant, @PathVariable UUID id) {
        service.delete(tenant.tenantId(), id);
    }

    @GetMapping("/{id}/secret")
    @Operation(summary = "Reveal the current signing secret")
    SecretResponse secret(@AuthenticationPrincipal TenantPrincipal tenant, @PathVariable UUID id) {
        return new SecretResponse(service.revealSecret(tenant.tenantId(), id));
    }

    @PostMapping("/{id}/secret/rotate")
    @Operation(summary = "Rotate the signing secret", description = "The previous secret keeps signing for 24 hours.")
    EndpointWithSecretResponse rotate(@AuthenticationPrincipal TenantPrincipal tenant, @PathVariable UUID id) {
        return EndpointWithSecretResponse.from(service.rotateSecret(tenant.tenantId(), id));
    }
}
