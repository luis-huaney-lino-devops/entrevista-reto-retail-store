package com.retailstore.api.comun.auditoria;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Cablea la auditoría de Spring Data: de dónde sale la fecha y de dónde sale el
 * autor.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "autorActual", dateTimeProviderRef = "proveedorFechaHora")
public class ConfiguracionAuditoria {

    /** Nombre que se registra cuando el cambio no viene de una sesión del panel. */
    public static final String AUTOR_SISTEMA = UsuarioActual.SISTEMA;

    /**
     * La hora sale del {@link Clock} inyectable, no de {@code Instant.now()}.
     * Es lo que permite a una prueba fijar el instante y afirmar sobre
     * {@code creadoEn} sin comparar contra un margen de tolerancia.
     */
    @Bean
    public DateTimeProvider proveedorFechaHora(Clock reloj) {
        return () -> Optional.of(reloj.instant());
    }

    /**
     * El autor es el usuario del panel que hizo la petición.
     *
     * <p>Devuelve siempre un valor: las columnas son NOT NULL, y una migración
     * de datos o el arranque de la aplicación escriben sin sesión. En ese caso
     * el autor es {@code sistema}, que es información -distinguir un cambio
     * automático de uno humano importa cuando hay que explicar un precio.
     */
    @Bean
    public AuditorAware<String> autorActual() {
        return () -> Optional.of(UsuarioActual.nombre());
    }
}
