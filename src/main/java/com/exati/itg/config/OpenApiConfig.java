package com.exati.itg.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI exatiItgOpenAPI(
            @Value("${spring.application.name}") String name,
            @Value("${info.app.version}")       String version
    ) {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste the JWT returned by POST /api/v1/auth/login");

        return new OpenAPI()
                .info(new Info()
                        .title(name)
                        .description("REST API for the Exati ITG platform")
                        .version(version)
                        .contact(new Contact().name("Exati"))
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearer));
    }
}
