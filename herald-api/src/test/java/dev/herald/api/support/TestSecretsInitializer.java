package dev.herald.api.support;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/** Registered in test {@code META-INF/spring.factories}; injects generated secrets before binding. */
public class TestSecretsInitializer implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        TestInfrastructure.registerSecrets(environment);
    }
}
