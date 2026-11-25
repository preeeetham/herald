package dev.herald.worker.support;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Generates secrets per test run so nothing credential-like is committed. */
public class TestSecrets implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        // ElasticMQ ignores credentials, but the AWS SDK requires some to be present.
        System.setProperty("aws.accessKeyId", "local");
        System.setProperty("aws.secretAccessKey", "local");
        environment.getPropertySources().addFirst(new MapPropertySource(
                "herald-test-secrets", Map.of("herald.secrets.encryption-key", Base64.getEncoder().encodeToString(key))));
    }
}
