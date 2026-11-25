package dev.herald.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.herald.core.signing.SigningSecret;
import dev.herald.core.signing.WebhookSigner;
import dev.herald.worker.delivery.DeliveryProcessor;
import dev.herald.worker.delivery.DeliveryStore;
import dev.herald.worker.support.Fixtures;
import dev.herald.worker.support.Receiver;
import dev.herald.worker.support.Receiver.Reply;
import dev.herald.worker.support.WorkerTestInfrastructure;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = {
    "herald.queue.create-if-missing=true",
    "herald.queue.wait-time=1s",
    "herald.dispatch.interval=50ms",
    "herald.delivery.allow-private-networks=true",
    "herald.delivery.request-timeout=1s",
    "herald.retry.schedule=200ms,200ms",
    "herald.retry.jitter=0",
    "herald.isolation.bulkhead-deferral=100ms",
    "herald.isolation.circuit-minimum-calls=1000",
    "herald.sweeper.interval=1h"
})
@Import(WorkerTestInfrastructure.class)
class DeliveryEngineIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static Receiver receiver;

    @Autowired Fixtures fixtures;
    @Autowired DeliveryProcessor processor;
    @Autowired DeliveryStore store;
    @Autowired JdbcClient jdbc;

    @BeforeAll
    static void startReceiver() throws Exception {
        receiver = new Receiver();
    }

    @AfterAll
    static void stopReceiver() {
        receiver.close();
    }

    @Test
    void deliversASignedStandardWebhooksEnvelope() {
        Fixtures.Endpoint endpoint = fixtures.endpoint(fixtures.tenant(10), receiver.url("/signed"));
        UUID delivery = fixtures.delivery(endpoint, "PENDING");

        awaitStatus(delivery, "SUCCEEDED");

        Receiver.Request request = receiver.requests("/signed").getFirst();
        assertThat(request.header("content-type")).isEqualTo("application/json");
        assertThat(request.body()).contains("\"type\":\"claim.created\"").contains("\"data\":{\"claimNumber\"");
        boolean valid = new WebhookSigner().verify(
                endpoint.secret(), request.header("webhook-id"), request.header("webhook-timestamp"),
                request.body(), request.header("webhook-signature"), Instant.now());
        assertThat(valid).isTrue();
        assertThat(fixtures.attempts(delivery)).isEqualTo(1);
    }

    @Test
    void retriesTransientFailuresThenSucceeds() {
        receiver.on("/flaky", call -> call == 1 ? Reply.status(503) : Reply.status(200));
        UUID delivery = fixtures.delivery(fixtures.endpoint(fixtures.tenant(10), receiver.url("/flaky")), "PENDING");

        awaitStatus(delivery, "SUCCEEDED");

        assertThat(fixtures.attempts(delivery)).isEqualTo(2);
        assertThat(fixtures.attemptOutcome(delivery, 1)).isEqualTo("RETRYING:HTTP 503");
    }

    @Test
    void deadLettersPermanentFailuresImmediately() {
        receiver.on("/gone", call -> Reply.status(410));
        UUID delivery = fixtures.delivery(fixtures.endpoint(fixtures.tenant(10), receiver.url("/gone")), "PENDING");

        awaitStatus(delivery, "DEAD");

        assertThat(fixtures.attempts(delivery)).isEqualTo(1);
    }

    @Test
    void deadLettersAfterTheRetryScheduleIsExhausted() {
        receiver.on("/down", call -> Reply.status(500));
        UUID delivery = fixtures.delivery(fixtures.endpoint(fixtures.tenant(10), receiver.url("/down")), "PENDING");

        awaitStatus(delivery, "DEAD");

        assertThat(fixtures.attempts(delivery)).isEqualTo(3); // first attempt + two retries
    }

    @Test
    void slowReceiversTimeOutAndAreRetried() {
        receiver.on("/slow", call -> call == 1 ? Reply.slow(200, 2_000) : Reply.status(200));
        UUID delivery = fixtures.delivery(fixtures.endpoint(fixtures.tenant(10), receiver.url("/slow")), "PENDING");

        awaitStatus(delivery, "SUCCEEDED");

        assertThat(fixtures.attemptOutcome(delivery, 1)).startsWith("RETRYING:Timed out");
    }

    @Test
    void aNoisyTenantCannotSlowDownOtherTenants() {
        receiver.on("/noisy", call -> Reply.slow(200, 800));
        Fixtures.Endpoint noisy = fixtures.endpoint(fixtures.tenant(2), receiver.url("/noisy"));
        Fixtures.Endpoint quiet = fixtures.endpoint(fixtures.tenant(10), receiver.url("/quiet"));
        List<UUID> noisyDeliveries = IntStream.range(0, 20).mapToObj(i -> fixtures.delivery(noisy, "PENDING")).toList();
        List<UUID> quietDeliveries = IntStream.range(0, 5).mapToObj(i -> fixtures.delivery(quiet, "PENDING")).toList();

        // The quiet tenant finishes long before the noisy backlog (20 × 800 ms at concurrency 2 ≈ 8 s).
        await().atMost(Duration.ofSeconds(4))
                .until(() -> quietDeliveries.stream().allMatch(d -> fixtures.status(d).equals("SUCCEEDED")));
        assertThat(noisyDeliveries.stream().filter(d -> fixtures.status(d).equals("SUCCEEDED")).count()).isLessThan(20);

        await().atMost(TIMEOUT).until(() -> noisyDeliveries.stream().allMatch(d -> fixtures.status(d).equals("SUCCEEDED")));
        assertThat(receiver.peakConcurrency("/noisy")).isLessThanOrEqualTo(2);
    }

    @Test
    void duplicateQueueMessagesAreDeliveredOnce() throws Exception {
        UUID delivery = fixtures.delivery(fixtures.endpoint(fixtures.tenant(10), receiver.url("/once")), "QUEUED");

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 5; i++) {
                pool.submit(() -> processor.process(delivery));
            }
        }

        assertThat(receiver.requests("/once")).hasSize(1);
        assertThat(fixtures.status(delivery)).isEqualTo("SUCCEEDED");
    }

    @Test
    void sendsBothSignaturesWhileASecretRotationOverlaps() {
        SigningSecret previous = SigningSecret.generate();
        Fixtures.Endpoint endpoint = fixtures.endpoint(fixtures.tenant(10), receiver.url("/rotating"), previous);
        UUID delivery = fixtures.delivery(endpoint, "PENDING");

        awaitStatus(delivery, "SUCCEEDED");

        Receiver.Request request = receiver.requests("/rotating").getFirst();
        assertThat(request.header("webhook-signature").split(" ")).hasSize(2);
        assertThat(new WebhookSigner().verify(previous, request.header("webhook-id"), request.header("webhook-timestamp"),
                request.body(), request.header("webhook-signature"), Instant.now())).isTrue();
    }

    @Test
    void disabledEndpointsAreDeadLetteredWithoutACall() {
        Fixtures.Endpoint endpoint = fixtures.endpoint(fixtures.tenant(10), receiver.url("/disabled"));
        fixtures.disable(endpoint.id());
        UUID delivery = fixtures.delivery(endpoint, "PENDING");

        awaitStatus(delivery, "DEAD");

        assertThat(receiver.requests("/disabled")).isEmpty();
    }

    @Test
    void sweeperRecoversDeliveriesAbandonedByACrashedWorker() {
        UUID delivery = fixtures.delivery(fixtures.endpoint(fixtures.tenant(10), receiver.url("/recovered")), "PENDING");
        // Pretend a worker claimed it 15 minutes ago and died.
        jdbc.sql("UPDATE delivery SET status = 'DELIVERING' WHERE id = :id").param("id", delivery).update();
        fixtures.ageInFlight(delivery, "DELIVERING", 15);

        assertThat(store.releaseStale(Duration.ofMinutes(10))).isGreaterThanOrEqualTo(1);

        awaitStatus(delivery, "SUCCEEDED");
    }

    private void awaitStatus(UUID delivery, String status) {
        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(50)).until(() -> fixtures.status(delivery).equals(status));
    }
}
