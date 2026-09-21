package com.retailstore.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Orígenes admitidos. Se configuran con {@code CORS_ALLOWED_ORIGINS}. */
@ConfigurationProperties(prefix = "app.cors")
public record PropiedadesCors(List<String> origenesPermitidos) {

    public PropiedadesCors {
        origenesPermitidos = origenesPermitidos == null ? List.of() : List.copyOf(origenesPermitidos);
    }
}
