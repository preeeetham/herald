package dev.herald.api.controlplane;

import static org.assertj.core.api.Assertions.assertThat;

import dev.herald.api.support.Api;
import dev.herald.api.support.IntegrationTest;
import dev.herald.api.support.TestInfrastructure;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ControlPlaneIntegrationTest {

    private static final String ADMIN = TestInfrastructure.ADMIN_TOKEN;

    @Autowired
    MockMvc mvc;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(mvc);
    }

    @Test
    void onboardingReturnsAWorkingApiKey() {
        Api.Tenant tenant = api.onboard(unique("acme"));

        Api.Response me = api.get("/v1/me", tenant.apiKey());

        assertThat(me.status()).isEqualTo(200);
        assertThat(me.<String>json("$.id")).isEqualTo(tenant.id());
        assertThat(me.<Integer>json("$.rateLimitPerSecond")).isPositive();
    }

    @Test
    void duplicateSlugIsRejected() {
        String slug = unique("dup");
        api.onboard(slug);

        assertThat(api.post("/admin/v1/tenants", ADMIN, "{\"slug\":\"" + slug + "\",\"name\":\"Again\"}").status()).isEqualTo(409);
    }

    @Test
    void requestsWithoutValidTokenAreUnauthorized() {
        assertThat(api.get("/v1/me", null).status()).isEqualTo(401);
        assertThat(api.get("/v1/me", "hk_aaaaaaaa_" + "b".repeat(32)).status()).isEqualTo(401);
        assertThat(api.get("/admin/v1/tenants", "nope").status()).isEqualTo(401);
    }

    @Test
    void tenantKeysCannotUseAdminApiAndAdminCannotActAsTenant() {
        Api.Tenant tenant = api.onboard(unique("scoped"));

        assertThat(api.get("/admin/v1/tenants", tenant.apiKey()).status()).isEqualTo(403);
        assertThat(api.get("/v1/me", ADMIN).status()).isEqualTo(403);
    }

    @Test
    void validationErrorsAreReportedPerField() {
        Api.Response r = api.post("/admin/v1/tenants", ADMIN, "{\"slug\":\"Bad Slug\",\"name\":\"\"}");

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.<String>json("$.errors.slug")).isNotBlank();
        assertThat(r.<String>json("$.errors.name")).isNotBlank();
    }

    @Test
    void endpointLifecycle() {
        Api.Tenant tenant = api.onboard(unique("lifecycle"));

        Api.Response created = api.post("/v1/endpoints", tenant.apiKey(), """
                {"url": "https://partner.example.com/hooks", "description": "Repair network", "eventTypes": ["claim.*", "claim.*"]}""");
        assertThat(created.status()).isEqualTo(201);
        String id = created.json("$.endpoint.id");
        String secret = created.json("$.secret");
        assertThat(secret).startsWith("whsec_");
        assertThat(created.<List<String>>json("$.endpoint.eventTypes")).containsExactly("claim.*");

        assertThat(api.get("/v1/endpoints/" + id + "/secret", tenant.apiKey()).<String>json("$.secret")).isEqualTo(secret);

        Api.Response rotated = api.post("/v1/endpoints/" + id + "/secret/rotate", tenant.apiKey(), null);
        assertThat(rotated.<String>json("$.secret")).isNotEqualTo(secret);

        Api.Response disabled = api.patch("/v1/endpoints/" + id, tenant.apiKey(), "{\"status\":\"DISABLED\"}");
        assertThat(disabled.<String>json("$.status")).isEqualTo("DISABLED");

        assertThat(api.delete("/v1/endpoints/" + id, tenant.apiKey()).status()).isEqualTo(204);
        assertThat(api.get("/v1/endpoints/" + id, tenant.apiKey()).status()).isEqualTo(404);
    }

    @Test
    void endpointsAreInvisibleToOtherTenants() {
        Api.Tenant owner = api.onboard(unique("owner"));
        Api.Tenant other = api.onboard(unique("other"));
        String id = api.post("/v1/endpoints", owner.apiKey(), """
                {"url": "https://partner.example.com/hooks", "eventTypes": ["*"]}""").json("$.endpoint.id");

        assertThat(api.get("/v1/endpoints/" + id, other.apiKey()).status()).isEqualTo(404);
        assertThat(api.get("/v1/endpoints/" + id + "/secret", other.apiKey()).status()).isEqualTo(404);
        assertThat(api.delete("/v1/endpoints/" + id, other.apiKey()).status()).isEqualTo(404);
        assertThat(api.get("/v1/endpoints", other.apiKey()).body()).isEqualTo("[]");
    }

    @Test
    void ssrfTargetsAndBadPatternsAreRejected() {
        Api.Tenant tenant = api.onboard(unique("ssrf"));

        assertThat(api.post("/v1/endpoints", tenant.apiKey(), """
                {"url": "https://169.254.169.254/latest/meta-data", "eventTypes": ["*"]}""").status()).isEqualTo(400);
        assertThat(api.post("/v1/endpoints", tenant.apiKey(), """
                {"url": "http://partner.example.com", "eventTypes": ["*"]}""").status()).isEqualTo(400);
        assertThat(api.post("/v1/endpoints", tenant.apiKey(), """
                {"url": "https://partner.example.com", "eventTypes": ["Claim Created"]}""").status()).isEqualTo(400);
    }

    @Test
    void revokedKeysStopWorking() {
        Api.Tenant tenant = api.onboard(unique("revoke"));
        Api.Response second = api.post("/v1/api-keys", tenant.apiKey(), "{\"name\":\"ci\"}");
        String secondKey = second.json("$.key");

        assertThat(api.get("/v1/me", secondKey).status()).isEqualTo(200);
        assertThat(api.delete("/v1/api-keys/" + second.json("$.id"), tenant.apiKey()).status()).isEqualTo(204);
        assertThat(api.get("/v1/me", secondKey).status()).isEqualTo(401);
        assertThat(api.get("/v1/api-keys", tenant.apiKey()).body()).doesNotContain(secondKey);
    }

    @Test
    void suspendedTenantsAreLockedOut() {
        Api.Tenant tenant = api.onboard(unique("suspend"));

        api.patch("/admin/v1/tenants/" + tenant.id(), ADMIN, "{\"status\":\"SUSPENDED\"}");

        assertThat(api.get("/v1/me", tenant.apiKey()).status()).isEqualTo(401);
    }

    private static String unique(String base) {
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
