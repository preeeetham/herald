package dev.herald.worker.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real Postgres and an SQS-compatible broker (ElasticMQ) for worker integration tests. */
@TestConfiguration(proxyBeanMethods = false)
public class WorkerTestInfrastructure {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    GenericContainer<?> sqs() {
        return new GenericContainer<>("softwaremill/elasticmq-native:1.6.14")
                .withExposedPorts(9324)
                .waitingFor(Wait.forListeningPort());
    }

    @Bean
    DynamicPropertyRegistrar sqsProperties(GenericContainer<?> sqs) {
        return registry -> registry.add(
                "herald.queue.endpoint", () -> "http://" + sqs.getHost() + ":" + sqs.getMappedPort(9324));
    }
}
