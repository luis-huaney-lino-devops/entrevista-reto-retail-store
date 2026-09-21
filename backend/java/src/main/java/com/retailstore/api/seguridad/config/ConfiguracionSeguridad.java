package com.retailstore.api.seguridad.config;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Reglas de acceso.
 *
 * <p>La tienda es pública y el panel no. Todo lo que cuelga de
 * {@code /api/v1/admin} exige un token con audiencia {@code admin}; el resto de
 * la API se sirve sin autenticación porque un catálogo detrás de un login no es
 * una tienda.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(PropiedadesJwt.class)
public class ConfiguracionSeguridad {

    public static final String RUTA_ACCESO = "/api/v1/admin/acceso";
    public static final String RUTA_REFRESCO = "/api/v1/admin/refrescar";
    public static final String RUTA_SALIDA = "/api/v1/admin/salir";

    private final FiltroJwtPanel filtroJwt;
    private final HandlerExceptionResolver resolutorExcepciones;

    public ConfiguracionSeguridad(FiltroJwtPanel filtroJwt,
                                  @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolutorExcepciones) {
        this.filtroJwt = filtroJwt;
        this.resolutorExcepciones = resolutorExcepciones;
    }

    @Bean
    public SecurityFilterChain cadenaFiltros(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Sin CSRF porque no hay sesión de servidor ni autenticación por
                // cookie en las rutas que mutan: el acceso viaja en la cabecera
                // Authorization, que un formulario ajeno no puede poner. La
                // cookie de refresco sí existe, y lo que la protege es
                // SameSite=Lax: un POST cross-site no la lleva.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(RUTA_ACCESO, RUTA_REFRESCO, RUTA_SALIDA).permitAll()
                        .requestMatchers("/api/v1/admin/**").authenticated()
                        .requestMatchers("/swagger/**", "/swagger-ui/**", "/v3/api-docs/**", "/health").permitAll()
                        // El apretón de manos del WebSocket trae su propia
                        // credencial -un ticket o el token del hilo- y la
                        // valida su interceptor: el filtro de aquí no puede
                        // leer una cabecera Authorization que el navegador no
                        // deja poner.
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().permitAll())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((peticion, respuesta, ex) -> resolutorExcepciones.resolveException(
                                peticion, respuesta, null,
                                new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED, "Necesitas iniciar sesión.")))
                        .accessDeniedHandler((peticion, respuesta, ex) -> resolutorExcepciones.resolveException(
                                peticion, respuesta, null,
                                new ExcepcionAplicacion(CodigoError.FORBIDDEN,
                                        "No tienes permiso para esta operación."))))
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt coste 12, con prefijo de algoritmo.
     *
     * <p>El prefijo -{@code {bcrypt}}- es lo que permite subir el coste o
     * cambiar de algoritmo más adelante sin tocar las filas existentes: cada
     * hash dice con qué se generó, así que los viejos siguen verificándose
     * mientras los nuevos usan el parámetro nuevo.
     */
    @Bean
    public PasswordEncoder codificadorContrasenas() {
        String porOmision = "bcrypt";
        Map<String, PasswordEncoder> codificadores = new HashMap<>();
        codificadores.put(porOmision, new BCryptPasswordEncoder(12));
        return new DelegatingPasswordEncoder(porOmision, codificadores);
    }
}
