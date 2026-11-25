package dev.herald.api.stats;

import dev.herald.api.security.TenantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Stats")
@SecurityRequirement(name = "apiKey")
class StatsController {

    private final StatsService service;

    StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/v1/stats")
    @Operation(summary = "Delivery health", description = "Volume, success rate, latency percentiles, a time series and per-endpoint health.")
    Stats stats(@AuthenticationPrincipal TenantPrincipal tenant, @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours) {
        return service.forWindow(tenant.tenantId(), Duration.ofHours(hours));
    }
}
