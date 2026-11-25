package dev.herald.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.herald.api.support.Api;
import dev.herald.api.support.IntegrationTest;
import dev.herald.api.support.TestInfrastructure;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@IntegrationTest
class RateLimitIntegrationTest {

    private static final String EVENT = "{\"type\":\"policy.bound\",\"payload\":{\"policyNumber\":\"POL-1\"}}";

    @Autowired
    MockMvc mvc;

    @Test
    void rejectsBurstsBeyondTheTenantsBucketWithoutAffectingOtherTenants() throws Exception {
        Api api = new Api(mvc);
        String limited = onboard(api, 1, 3);
        String neighbour = onboard(api, 100, 100);

        for (int i = 0; i < 3; i++) {
            assertThat(publish(limited).getResponse().getStatus()).isEqualTo(202);
        }
        MvcResult rejected = publish(limited);

        assertThat(rejected.getResponse().getStatus()).isEqualTo(429);
        assertThat(rejected.getResponse().getHeader("Retry-After")).isEqualTo("1");
        assertThat(rejected.getResponse().getHeader("RateLimit-Remaining")).isEqualTo("0");
        assertThat(rejected.getResponse().getContentAsString()).contains("rate-limited");
        assertThat(publish(neighbour).getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    void reportsRemainingBudgetOnSuccess() throws Exception {
        String tenant = onboard(new Api(mvc), 10, 20);

        MvcResult ok = publish(tenant);

        assertThat(ok.getResponse().getHeader("RateLimit-Limit")).isEqualTo("20");
        assertThat(ok.getResponse().getHeader("RateLimit-Remaining")).isEqualTo("19");
    }

    @Test
    void readEndpointsAreNotRateLimited() throws Exception {
        String tenant = onboard(new Api(mvc), 1, 1);
        publish(tenant);

        assertThat(new Api(mvc).get("/v1/events", tenant).status()).isEqualTo(200);
    }

    private String onboard(Api api, int rps, int burst) {
        Api.Response r = api.post("/admin/v1/tenants", TestInfrastructure.ADMIN_TOKEN, """
                {"slug": "rl-%s", "name": "RL", "rateLimitPerSecond": %d, "rateLimitBurst": %d}"""
                .formatted(UUID.randomUUID().toString().substring(0, 8), rps, burst));
        return r.json("$.apiKey.key");
    }

    private MvcResult publish(String key) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/v1/events")
                        .header("Authorization", "Bearer " + key)
                        .contentType("application/json")
                        .content(EVENT))
                .andReturn();
    }
}
