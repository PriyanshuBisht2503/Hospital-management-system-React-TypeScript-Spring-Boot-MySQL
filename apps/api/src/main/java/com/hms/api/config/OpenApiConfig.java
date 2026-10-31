package com.hms.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI hospitalOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Hospital Management System (HMS) Core API")
                .version("1.0.0")
                .description("Core HMS REST API modelled on Aster Hospitals ecosystem. Serves public website, patient portal, and staff dashboard.")
                .contact(new Contact().name("HMS Engineering").email("support@hms.local")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME))
            .components(new Components()
                .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                    .name(BEARER_AUTH_SCHEME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Provide JWT access token in the format: Bearer <token>")));
    }
}
