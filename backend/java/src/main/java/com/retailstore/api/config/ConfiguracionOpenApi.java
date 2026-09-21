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
    public static final String ESQUEMA_TIENDA = "tokenTienda";

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
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_PANEL,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Token de acceso del panel. Se obtiene en "
                                                + "POST /api/v1/admin/acceso."))
                        // Dos esquemas y no uno: los tokens no son
                        // intercambiables. Que Swagger los pida por separado
                        // evita la confusion de probar /cuenta con el token del
                        // panel y recibir un 403 sin entender por que.
                        .addSecuritySchemes(ESQUEMA_TIENDA,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Token de acceso del cliente. Se obtiene en "
                                                + "POST /api/v1/cuenta/acceso o /api/v1/cuenta/google.")));
    }
}
