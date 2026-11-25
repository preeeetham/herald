package dev.herald.api.stats;

import static org.assertj.core.api.Assertions.assertThat;

import dev.herald.api.support.Api;
import dev.herald.api.support.IntegrationTest;
import dev.herald.core.id.Ids;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class StatsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void summarisesAttemptsForTheTenant() {
        Api api = new Api(mvc);
        Api.Tenant tenant = api.onboard("stats-" + UUID.randomUUID().toString().substring(0, 8));
        api.post("/v1/endpoints", tenant.apiKey(), "{\"url\":\"https://partner.example.com/h\",\"eventTypes\":[\"*\"]}");
        api.post("/v1/events", tenant.apiKey(), "{\"type\":\"claim.created\",\"payload\":{}}");
        UUID delivery = UUID.fromString(api.get("/v1/deliveries", tenant.apiKey()).json("$.data[0].id"));
        attempt(tenant, delivery, 1, "RETRYING", 503, 900);
        attempt(tenant, delivery, 2, "SUCCEEDED", 200, 100);

        Api.Response stats = api.get("/v1/stats?hours=1", tenant.apiKey());

        assertThat(stats.status()).isEqualTo(200);
        assertThat(stats.<Integer>json("$.events")).isEqualTo(1);
        assertThat(stats.<Integer>json("$.attempts.total")).isEqualTo(2);
        assertThat(stats.<Double>json("$.attempts.successRate")).isEqualTo(0.5);
        assertThat(stats.<Double>json("$.latencyMs.p50")).isEqualTo(500.0);
        assertThat(stats.<Integer>json("$.endpoints[0].attempts")).isEqualTo(2);
        assertThat(stats.<java.util.List<?>>json("$.series")).isNotEmpty();
    }

    private void attempt(Api.Tenant tenant, UUID delivery, int n, String outcome, int status, int ms) {
        jdbc.sql("""
                INSERT INTO delivery_attempt (id, delivery_id, tenant_id, attempt_number, outcome, status_code, started_at, duration_ms)
                VALUES (:id, :d, :t, :n, :o, :s, :at, :ms)
                """)
                .param("id", Ids.newId()).param("d", delivery).param("t", UUID.fromString(tenant.id()))
                .param("n", n).param("o", outcome).param("s", status).param("at", Timestamp.from(Instant.now())).param("ms", ms)
                .update();
    }
}
