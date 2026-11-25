package dev.herald.api.security;

import dev.herald.api.apikey.ApiKeyService;
import dev.herald.api.config.HeraldProperties;
import dev.herald.api.tenant.TenantRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, HeraldProperties properties, ApiKeyService apiKeys, TenantRepository tenants) throws Exception {
        var apiKeyFilter = new ApiKeyAuthenticationFilter(properties.adminToken(), apiKeys, tenants);
        return http
                .csrf(AbstractHttpConfigurer::disable) // token-authenticated API, no cookies
                .cors(cors -> {})
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(apiKeyFilter, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/admin/**").hasAuthority(ApiKeyAuthenticationFilter.ROLE_ADMIN)
                        .requestMatchers("/v1/**").hasAuthority(ApiKeyAuthenticationFilter.ROLE_TENANT)
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "A valid bearer token is required"))
                        .accessDeniedHandler((request, response, e) ->
                                writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "This token cannot access this resource")))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(HeraldProperties properties) {
        var config = new CorsConfiguration();
        List<String> origins = properties.cors().allowedOrigins();
        config.setAllowedOrigins(origins == null ? List.of() : origins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setExposedHeaders(List.of("RateLimit-Limit", "RateLimit-Remaining", "RateLimit-Reset", "Retry-After"));
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeProblem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}".formatted(title, status, detail));
    }
}
