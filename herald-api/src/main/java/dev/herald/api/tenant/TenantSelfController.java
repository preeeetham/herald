package dev.herald.api.tenant;

import dev.herald.api.security.TenantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Tenant")
@SecurityRequirement(name = "apiKey")
class TenantSelfController {

    private final TenantService service;

    TenantSelfController(TenantService service) {
        this.service = service;
    }

    @GetMapping("/v1/me")
    @Operation(summary = "The tenant that owns the calling API key, with its plan limits")
    TenantResponse me(@AuthenticationPrincipal TenantPrincipal principal) {
        return TenantResponse.from(service.get(principal.tenantId()));
    }
}
