package dev.herald.api.tenant;

import dev.herald.api.apikey.IssuedApiKey;
import dev.herald.api.web.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Platform-operator API for onboarding tenants and managing their plan limits. */
@RestController
@Validated
@RequestMapping("/admin/v1/tenants")
@Tag(name = "Admin · Tenants")
@SecurityRequirement(name = "adminToken")
class AdminTenantController {

    private final TenantService service;

    AdminTenantController(TenantService service) {
        this.service = service;
    }

    record CreateTenantRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}$", message = "must be lowercase letters, digits and dashes") String slug,
            @NotBlank @Size(max = 200) String name,
            @Min(1) @Max(100_000) Integer rateLimitPerSecond,
            @Min(1) @Max(1_000_000) Integer rateLimitBurst,
            @Min(1) @Max(10_000) Integer maxConcurrency) {}

    record UpdateTenantRequest(
            @Size(min = 1, max = 200) String name,
            TenantStatus status,
            @Min(1) @Max(100_000) Integer rateLimitPerSecond,
            @Min(1) @Max(1_000_000) Integer rateLimitBurst,
            @Min(1) @Max(10_000) Integer maxConcurrency) {}

    record OnboardedResponse(TenantResponse tenant, IssuedApiKey apiKey) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Onboard a tenant", description = "Creates the tenant and returns its first API key. The key is shown only once.")
    OnboardedResponse create(@Valid @RequestBody CreateTenantRequest request) {
        var onboarded = service.create(
                request.slug(), request.name(), request.rateLimitPerSecond(), request.rateLimitBurst(), request.maxConcurrency());
        return new OnboardedResponse(TenantResponse.from(onboarded.tenant()), onboarded.apiKey());
    }

    @GetMapping
    @Operation(summary = "List tenants")
    Page<TenantResponse> list(
            @RequestParam(required = false) UUID after, @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return service.list(after, limit).map(TenantResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a tenant")
    TenantResponse get(@PathVariable UUID id) {
        return TenantResponse.from(service.get(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a tenant", description = "Change name, limits, or suspend / reactivate.")
    TenantResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTenantRequest request) {
        return TenantResponse.from(service.update(
                id, request.name(), request.status(), request.rateLimitPerSecond(), request.rateLimitBurst(), request.maxConcurrency()));
    }
}
