package dev.herald.api.support;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    public static final String ADMIN_TOKEN = "test-admin-" + randomBase64(18);

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
    }

    /**
     * Secrets are generated per test run rather than committed, so the repository never contains
     * anything that looks like a credential.
     */
    static void registerSecrets(ConfigurableEnvironment environment) {
        environment.getPropertySources().addFirst(new MapPropertySource("herald-test-secrets", Map.of(
                "herald.admin-token", ADMIN_TOKEN,
                "herald.secrets.encryption-key", randomBase64(32))));
    }

    private static String randomBase64(int bytes) {
        byte[] buffer = new byte[bytes];
        new SecureRandom().nextBytes(buffer);
        return Base64.getEncoder().encodeToString(buffer);
    }
}
