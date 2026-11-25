package dev.herald.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI heraldOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Herald API")
                        .version("v1")
                        .description("Multi-tenant, reliable event delivery. Publish once; Herald signs, delivers, retries and records every attempt.")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes("apiKey", bearer("Tenant API key: hk_<prefix>_<secret>"))
                        .addSecuritySchemes("adminToken", bearer("Platform admin token")));
    }

    private static SecurityScheme bearer(String description) {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").description(description);
    }
}
