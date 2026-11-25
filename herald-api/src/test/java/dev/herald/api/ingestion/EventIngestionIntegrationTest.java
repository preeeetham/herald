package dev.herald.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.herald.api.support.Api;
import dev.herald.api.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@IntegrationTest
class EventIngestionIntegrationTest {

    private static final String CLAIM = """
            {"type": "claim.created", "payload": {"claimNumber": "CLM-1001", "policyNumber": "POL-77", "lossDate": "2026-11-20"}}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    Api api;
    Api.Tenant tenant;

    @BeforeEach
    void setUp() {
        api = new Api(mvc);
        tenant = api.onboard("ingest-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Test
    void fansOutOnlyToEnabledSubscribedEndpoints() {
        String claims = endpoint("[\"claim.*\"]");
        String everything = endpoint("[\"*\"]");
        endpoint("[\"policy.*\"]");
        String disabled = endpoint("[\"claim.created\"]");
        api.patch("/v1/endpoints/" + disabled, tenant.apiKey(), "{\"status\":\"DISABLED\"}");

        Api.Response published = api.post("/v1/events", tenant.apiKey(), CLAIM);

        assertThat(published.status()).isEqualTo(202);
        assertThat(published.<Integer>json("$.deliveries")).isEqualTo(2);
        Api.Response event = api.get("/v1/events/" + published.json("$.id"), tenant.apiKey());
        assertThat(event.<List<String>>json("$.deliveries[*].endpointId")).containsExactlyInAnyOrder(claims, everything);
        assertThat(event.<List<String>>json("$.deliveries[*].status")).containsOnly("PENDING");
        assertThat(event.<String>json("$.payload.claimNumber")).isEqualTo("CLM-1001");
    }

    @Test
    void sameIdempotencyKeyReturnsTheOriginalEvent() {
        endpoint("[\"*\"]");

        Api.Response first = publishWithKey("key-1", CLAIM);
        Api.Response second = publishWithKey("key-1", CLAIM.replace("{\"claimNumber\"", "{ \"claimNumber\""));

        assertThat(first.status()).isEqualTo(202);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.<String>json("$.id")).isEqualTo(first.json("$.id"));
        assertThat(countDeliveries()).isEqualTo(1);
    }

    @Test
    void reusingAnIdempotencyKeyForADifferentRequestIsAConflict() {
        publishWithKey("key-2", CLAIM);

        Api.Response reused = publishWithKey("key-2", CLAIM.replace("CLM-1001", "CLM-9999"));

        assertThat(reused.status()).isEqualTo(409);
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateExactlyOneEvent() throws Exception {
        endpoint("[\"*\"]");
        List<Callable<Integer>> calls = IntStream.range(0, 8)
                .<Callable<Integer>>mapToObj(i -> () -> publishWithKey("race", CLAIM).status())
                .toList();

        try (var pool = Executors.newFixedThreadPool(8)) {
            var statuses = pool.invokeAll(calls).stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
            assertThat(statuses).containsOnly(200, 202).containsOnlyOnce(202);
        }
        assertThat(countDeliveries()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidEvents() {
        assertThat(api.post("/v1/events", tenant.apiKey(), "{\"type\":\"Claim Created\",\"payload\":{}}").status()).isEqualTo(400);
        assertThat(api.post("/v1/events", tenant.apiKey(), "{\"type\":\"claim.created\",\"payload\":[1,2]}").status()).isEqualTo(400);
        assertThat(api.post("/v1/events", tenant.apiKey(), "{\"type\":\"claim.created\"}").status()).isEqualTo(400);
        String huge = "x".repeat(300_000);
        assertThat(api.post("/v1/events", tenant.apiKey(), "{\"type\":\"claim.created\",\"payload\":{\"blob\":\"" + huge + "\"}}").status())
                .isEqualTo(413);
    }

    @Test
    void eventsAreScopedToTheirTenant() {
        String id = api.post("/v1/events", tenant.apiKey(), CLAIM).json("$.id");
        Api.Tenant other = api.onboard("other-" + UUID.randomUUID().toString().substring(0, 8));

        assertThat(api.get("/v1/events/" + id, other.apiKey()).status()).isEqualTo(404);
        assertThat(api.get("/v1/events", other.apiKey()).<List<?>>json("$.data")).isEmpty();
    }

    @Test
    void listsEventsNewestFirstWithCursor() {
        for (int i = 0; i < 3; i++) {
            api.post("/v1/events", tenant.apiKey(), CLAIM);
        }

        Api.Response page1 = api.get("/v1/events?limit=2", tenant.apiKey());
        Api.Response page2 = api.get("/v1/events?limit=2&before=" + page1.json("$.nextCursor"), tenant.apiKey());

        assertThat(page1.<List<?>>json("$.data")).hasSize(2);
        assertThat(page2.<List<?>>json("$.data")).hasSize(1);
        assertThat(page2.<Object>json("$.nextCursor")).isNull();
    }

    @Test
    void onlyFinishedDeliveriesCanBeReplayed() {
        endpoint("[\"*\"]");
        api.post("/v1/events", tenant.apiKey(), CLAIM);
        String deliveryId = api.get("/v1/deliveries", tenant.apiKey()).json("$.data[0].id");

        assertThat(api.post("/v1/deliveries/" + deliveryId + "/replay", tenant.apiKey(), null).status()).isEqualTo(409);

        jdbc.sql("UPDATE delivery SET status = 'DEAD', attempts_in_cycle = 9 WHERE id = :id").param("id", UUID.fromString(deliveryId)).update();
        Api.Response replayed = api.post("/v1/deliveries/" + deliveryId + "/replay", tenant.apiKey(), null);

        assertThat(replayed.status()).isEqualTo(200);
        assertThat(replayed.<String>json("$.status")).isEqualTo("PENDING");
        assertThat(jdbc.sql("SELECT attempts_in_cycle FROM delivery WHERE id = :id").param("id", UUID.fromString(deliveryId))
                .query(Integer.class).single()).isZero();
    }

    @Test
    void bulkReplayRequeuesDeadDeliveries() {
        endpoint("[\"*\"]");
        api.post("/v1/events", tenant.apiKey(), CLAIM);
        api.post("/v1/events", tenant.apiKey(), CLAIM);
        jdbc.sql("UPDATE delivery SET status = 'DEAD' WHERE tenant_id = :t").param("t", UUID.fromString(tenant.id())).update();

        Api.Response r = api.post("/v1/deliveries/replay", tenant.apiKey(), "{\"status\":\"DEAD\"}");

        assertThat(r.<Integer>json("$.replayed")).isEqualTo(2);
        assertThat(api.get("/v1/deliveries?status=PENDING", tenant.apiKey()).<List<?>>json("$.data")).hasSize(2);
    }

    private String endpoint(String eventTypes) {
        return api.post("/v1/endpoints", tenant.apiKey(), """
                {"url": "https://partner.example.com/hooks", "eventTypes": %s}""".formatted(eventTypes)).json("$.endpoint.id");
    }

    private Api.Response publishWithKey(String key, String body) {
        return api.exec(MockMvcRequestBuilders.post("/v1/events").header("Idempotency-Key", key), tenant.apiKey(), body);
    }

    private int countDeliveries() {
        return jdbc.sql("SELECT count(*) FROM delivery WHERE tenant_id = :t").param("t", UUID.fromString(tenant.id())).query(Integer.class).single();
    }
}
