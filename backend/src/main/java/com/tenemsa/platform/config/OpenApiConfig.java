package com.tenemsa.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI at /swagger-ui.html, raw OpenAPI spec at /v3/api-docs.
 * Both need explicit permitAll entries in SecurityConfig — this app's
 * security filter chain ends in anyRequest().authenticated(), which guards
 * every path by default, not just /api/**, so springdoc's own endpoints
 * would otherwise demand a JWT just to view the documentation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tenemsaOpenAPI() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Tenemsa API")
                        .description("Multi-tenant HR / attendance / leave / team collaboration platform. " +
                                "Most endpoints require a Bearer JWT — use \"Authorize\" below with a token from " +
                                "POST /api/auth/login or POST /api/admin/login.")
                        .version("v1 (pre-release — no versioning prefix yet, see docs/roadmap.md)"))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
