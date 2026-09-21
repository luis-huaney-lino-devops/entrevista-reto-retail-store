package com.retailstore.api.config;

import com.retailstore.api.comun.error.Correlacion;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS.
 *
 * <p>Se declara como {@link CorsConfigurationSource} y no con
 * {@code WebMvcConfigurer} porque la cadena de filtros de seguridad corre
 * <strong>antes</strong> que MVC: una preflight rechazada por el filtro nunca
 * llegaría a la configuración de MVC, y el navegador vería un error de CORS en
 * lugar de la respuesta real.
 *
 * <p><strong>El bean se llama {@code corsConfigurationSource} a propósito.</strong>
 * Spring Security lo busca por ese nombre exacto. Con cualquier otro, la
 * aplicación arranca sin quejarse, el bean existe, la configuración es correcta
 * y aun así toda preflight responde 403: el filtro usa una configuración vacía
 * porque no encontró la nuestra. Es un fallo silencioso y caro de diagnosticar,
 * así que el nombre no se cambia.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesCors.class)
public class ConfiguracionWeb {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionWeb.class);

    private final PropiedadesCors propiedades;

    public ConfiguracionWeb(PropiedadesCors propiedades) {
        this.propiedades = propiedades;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Se registra en el arranque: un CORS mal configurado se manifiesta como
        // "no funciona nada" en el navegador, y esta línea dice en un segundo si
        // el origen del frontend está en la lista.
        log.info("CORS: orígenes permitidos {}", propiedades.origenesPermitidos());

        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(propiedades.origenesPermitidos());
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("*"));
        // La cookie de refresco viaja en peticiones cross-origin desde el panel
        // y la tienda. Con credenciales, el origen nunca puede ser "*": por eso
        // la lista es explícita y se configura por entorno.
        configuracion.setAllowCredentials(true);
        // Sin exponerla, el navegador se la oculta a JavaScript y el frontend no
        // puede mostrar el identificador de correlación al reportar un fallo.
        configuracion.setExposedHeaders(List.of(Correlacion.CABECERA));
        configuracion.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }
}
