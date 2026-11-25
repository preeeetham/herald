package dev.herald.api.apikey;

import dev.herald.api.security.TenantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api-keys")
@Tag(name = "API keys")
@SecurityRequirement(name = "apiKey")
class ApiKeyController {

    private final ApiKeyService service;

    ApiKeyController(ApiKeyService service) {
        this.service = service;
    }

    record CreateApiKeyRequest(@NotBlank @Size(max = 100) String name) {}

    record ApiKeyResponse(UUID id, String name, String prefix, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {
        static ApiKeyResponse from(ApiKeyRecord r) {
            return new ApiKeyResponse(r.id(), r.name(), r.prefix(), r.createdAt(), r.lastUsedAt(), r.revokedAt());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an API key", description = "The full key is returned only in this response.")
    IssuedApiKey create(@AuthenticationPrincipal TenantPrincipal tenant, @Valid @RequestBody CreateApiKeyRequest request) {
        return service.issue(tenant.tenantId(), request.name());
    }

    @GetMapping
    @Operation(summary = "List API keys")
    List<ApiKeyResponse> list(@AuthenticationPrincipal TenantPrincipal tenant) {
        return service.list(tenant.tenantId()).stream().map(ApiKeyResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke an API key")
    void revoke(@AuthenticationPrincipal TenantPrincipal tenant, @PathVariable UUID id) {
        service.revoke(tenant.tenantId(), id);
    }
}
