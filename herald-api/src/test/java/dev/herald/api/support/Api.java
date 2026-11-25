package dev.herald.api.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Small fluent helper so integration tests read like API usage. */
public final class Api {

    private final MockMvc mvc;

    public Api(MockMvc mvc) {
        this.mvc = mvc;
    }

    public record Response(int status, String body) {
        public <T> T json(String path) {
            return JsonPath.read(body, path);
        }
    }

    public record Tenant(String id, String apiKey) {}

    public Tenant onboard(String slug) {
        Response r = post("/admin/v1/tenants", TestInfrastructure.ADMIN_TOKEN, """
                {"slug": "%s", "name": "%s Insurance"}""".formatted(slug, slug));
        if (r.status() != 201) {
            throw new AssertionError("Onboarding failed: " + r);
        }
        return new Tenant(r.json("$.tenant.id"), r.json("$.apiKey.key"));
    }

    public Response get(String path, String token) {
        return exec(MockMvcRequestBuilders.get(path), token, null);
    }

    public Response post(String path, String token, String json) {
        return exec(MockMvcRequestBuilders.post(path), token, json);
    }

    public Response patch(String path, String token, String json) {
        return exec(MockMvcRequestBuilders.patch(path), token, json);
    }

    public Response delete(String path, String token) {
        return exec(MockMvcRequestBuilders.delete(path), token, null);
    }

    public Response exec(MockHttpServletRequestBuilder request, String token, String json) {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        try {
            MvcResult result = mvc.perform(request).andReturn();
            return new Response(result.getResponse().getStatus(), result.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
