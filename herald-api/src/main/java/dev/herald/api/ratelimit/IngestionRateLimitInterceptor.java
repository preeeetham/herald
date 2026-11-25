package dev.herald.api.ratelimit;

import dev.herald.api.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the tenant's token bucket to event publishing and reports the state in {@code
 * RateLimit-*} headers on every response, so well-behaved clients can pace themselves.
 */
@Component
class IngestionRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;

    IngestionRateLimitInterceptor(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal tenant)) {
            return true; // security has already rejected the request
        }
        RateLimiter.Decision decision = limiter.tryConsume(tenant.tenantId(), tenant.rateLimitPerSecond(), tenant.rateLimitBurst());
        long resetSeconds = (long) Math.ceil(decision.retryAfter().toMillis() / 1000.0);
        response.setHeader("RateLimit-Limit", Long.toString(decision.limit()));
        response.setHeader("RateLimit-Remaining", Long.toString(decision.remaining()));
        response.setHeader("RateLimit-Reset", Long.toString(resetSeconds));
        if (decision.allowed()) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(Math.max(1, resetSeconds)));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"https://github.com/preeeetham/herald/blob/main/docs/errors.md#rate-limited","title":"Too Many Requests",\
                "status":429,"detail":"Ingestion rate limit of %d events/s (burst %d) exceeded"}"""
                .formatted(tenant.rateLimitPerSecond(), tenant.rateLimitBurst()));
        return false;
    }
}
