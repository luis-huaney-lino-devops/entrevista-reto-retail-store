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
 * <p>Tres zonas y no dos. Todo lo que cuelga de {@code /api/v1/admin} exige un
 * token con audiencia {@code admin}; todo lo que cuelga de
 * {@code /api/v1/cuenta} exige uno con audiencia de tienda; el resto se sirve
 * sin autenticación porque un catálogo detrás de un login no es una tienda.
 *
 * <p>Las dos zonas autenticadas no se solapan en ninguna dirección: cada una
 * tiene su filtro y su filtro exige su audiencia, así que un token del panel es
 * tan inútil en {@code /cuenta} como uno de cliente en {@code /admin}.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(PropiedadesJwt.class)
public class ConfiguracionSeguridad {

    public static final String RAIZ_PANEL = "/api/v1/admin";
    public static final String RAIZ_CUENTA = "/api/v1/cuenta";

    /**
     * Las órdenes de la tienda. Son del mundo del cliente aunque no cuelguen de
     * {@code /cuenta}: quien confirma una compra o consulta su historial lo hace
     * con un token de tienda.
     *
     * <p>Tiene que estar aquí porque {@link FiltroJwtTienda} decide por prefijo
     * en qué rutas mira la cabecera {@code Authorization}. Sin esta constante el
     * filtro se saltaba {@code /api/v1/ordenes}, y ahí nadie quedaba
     * autenticado: el historial respondía 401 y —peor y en silencio— el checkout
     * recibía el principal nulo y registraba como <strong>invitada</strong> la
     * compra de alguien que sí tenía la sesión abierta.
     */
    public static final String RAIZ_ORDENES = "/api/v1/ordenes";

    public static final String RUTA_ACCESO = RAIZ_PANEL + "/acceso";
    public static final String RUTA_REFRESCO = RAIZ_PANEL + "/refrescar";
    public static final String RUTA_SALIDA = RAIZ_PANEL + "/salir";

    /**
     * Lo que se puede hacer en {@code /cuenta} sin haber entrado todavía.
     *
     * <p>Son los cinco puntos por los que se entra y se sale. {@code /salir}
     * está aquí a propósito: cerrar sesión con el token ya caducado tiene que
     * borrar la cookie igualmente, y exigir sesión para salir deja al cliente
     * con una cookie que no se puede quitar.
     */
    private static final String[] RUTAS_CUENTA_PUBLICAS = {
            RAIZ_CUENTA + "/registro",
            RAIZ_CUENTA + "/acceso",
            RAIZ_CUENTA + "/google",
            RAIZ_CUENTA + "/refrescar",
            RAIZ_CUENTA + "/salir",
            // Recuperar la contraseña tiene que ser publico: quien lo necesita
            // es justamente quien no puede entrar. Exigir sesion aqui seria
            // pedir la llave para poder pedir la llave.
            RAIZ_CUENTA + "/recuperar",
            RAIZ_CUENTA + "/restablecer",
            // Confirmar el correo se hace pulsando un enlace, y quien lo pulsa
            // puede abrirlo en otro navegador donde no tiene sesion.
            //
            // Sin comodin a proposito: asi /verificacion/reenviar NO entra aqui
            // y cae en el `authenticated()` de abajo, que es lo que debe ser
            // -lo pide quien ya entro y ve el aviso de "sin verificar"-.
            RAIZ_CUENTA + "/verificacion"};

    private final FiltroJwtPanel filtroJwt;
    private final FiltroJwtTienda filtroJwtTienda;
    private final HandlerExceptionResolver resolutorExcepciones;

    public ConfiguracionSeguridad(FiltroJwtPanel filtroJwt,
                                  FiltroJwtTienda filtroJwtTienda,
                                  @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolutorExcepciones) {
        this.filtroJwt = filtroJwt;
        this.filtroJwtTienda = filtroJwtTienda;
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
                        .requestMatchers(RAIZ_PANEL + "/**").authenticated()
                        .requestMatchers(RUTAS_CUENTA_PUBLICAS).permitAll()
                        .requestMatchers(RAIZ_CUENTA + "/**").authenticated()
                        // El historial de compras es del cliente que lo pide. Sin
                        // esta regla caeria en el permitAll de /api/** de abajo y
                        // llegaria al controlador con el principal nulo.
                        .requestMatchers("/api/v1/ordenes/mios").authenticated()
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
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(filtroJwtTienda, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Las rutas de sesión no miran la cabecera {@code Authorization}.
     *
     * <p>Lo usa {@link FiltroJwtTienda}, y donde importa es en {@code /salir}:
     * una tienda que adjunta el token de acceso a todas sus llamadas cerraría
     * sesión con uno ya caducado, y validarlo daría un 401 en lugar del 204 que
     * borra la cookie -el cliente se quedaría sin poder salir justo cuando más
     * quiere hacerlo-. En las otras cuatro un token tampoco aporta nada: son
     * los puntos por los que se entra.
     */
    public static boolean esRutaDeSesionDeCuenta(String uri) {
        for (String ruta : RUTAS_CUENTA_PUBLICAS) {
            if (ruta.equals(uri)) {
                return true;
            }
        }
        return false;
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
