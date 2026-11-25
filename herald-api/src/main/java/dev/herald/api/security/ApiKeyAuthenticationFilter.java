package dev.herald.api.security;

import dev.herald.api.apikey.ApiKeyService;
import dev.herald.api.tenant.Tenant;
import dev.herald.api.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates {@code Authorization: Bearer <token>} as either the platform admin or a tenant API
 * key. Requests without a valid token continue unauthenticated and are rejected by the
 * authorization rules, so this filter never decides access on its own.
 */
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String ROLE_ADMIN = "ROLE_ADMIN";
    static final String ROLE_TENANT = "ROLE_TENANT";
    private static final String BEARER = "Bearer ";

    private final byte[] adminToken;
    private final ApiKeyService apiKeys;
    private final TenantRepository tenants;

    ApiKeyAuthenticationFilter(String adminToken, ApiKeyService apiKeys, TenantRepository tenants) {
        this.adminToken = adminToken.getBytes(StandardCharsets.UTF_8);
        this.apiKeys = apiKeys;
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            authenticate(header.substring(BEARER.length()).trim())
                    .ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));
        }
        chain.doFilter(request, response);
    }

    private Optional<Authentication> authenticate(String token) {
        if (MessageDigest.isEqual(adminToken, token.getBytes(StandardCharsets.UTF_8))) {
            return Optional.of(authenticated(new AdminPrincipal(), ROLE_ADMIN));
        }
        return apiKeys.authenticate(token)
                .flatMap(key -> tenants.findById(key.tenantId()).filter(Tenant::isActive).map(tenant -> authenticated(
                        new TenantPrincipal(tenant.id(), tenant.slug(), key.id(), tenant.rateLimitPerSecond(), tenant.rateLimitBurst()),
                        ROLE_TENANT)));
    }

    private static Authentication authenticated(Object principal, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
