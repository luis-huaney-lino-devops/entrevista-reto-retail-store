package com.retailstore.api.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj inyectable.
 *
 * <p>Nadie llama a {@code Instant.now()} directamente. La vigencia de un cupón,
 * la caducidad de un token y las fechas de auditoría se pueden fijar en una
 * prueba porque todas pasan por este bean.
 */
@Configuration
public class ConfiguracionReloj {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
