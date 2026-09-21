package com.retailstore.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionOpenApi {

    public static final String ESQUEMA_PANEL = "tokenPanel";

    @Bean
    public OpenAPI apiRetailStore() {
        return new OpenAPI()
                .info(new Info()
                        .title("Retail Store API")
                        .version("v1")
                        .description("""
                                API REST del e-commerce Retail Store.

                                Los errores siguen RFC 9457 (application/problem+json) con las \
                                extensiones code y correlationId. El cliente se ramifica por code, \
                                nunca por detail."""))
                .components(new Components().addSecuritySchemes(ESQUEMA_PANEL,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token de acceso del panel. Se obtiene en POST /api/v1/admin/acceso.")));
    }
}
