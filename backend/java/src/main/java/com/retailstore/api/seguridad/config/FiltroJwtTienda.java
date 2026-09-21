package com.retailstore.api.seguridad.config;

import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.seguridad.dominio.ClienteAutenticado;
import com.retailstore.api.seguridad.servicio.ServicioJwt;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * El equivalente de {@link FiltroJwtPanel} para la tienda.
 *
 * <p>Dos filtros y no uno con un {@code if} porque cada uno exige una audiencia
 * distinta, y el que exige la del panel tiene que seguir rechazando un token de
 * cliente. Cada filtro corre <strong>solo</strong> sobre su prefijo de ruta
 * ({@link #shouldNotFilter}): si los dos miraran la misma cabecera
 * {@code Authorization}, el primero en ejecutarse rechazaría por audiencia el
 * token legítimo del otro mundo.
 *
 * <p>Sin cabecera, deja pasar: decidir si la ruta exige sesión es trabajo de
 * las reglas de autorización.
 */
@Component
public class FiltroJwtTienda extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final ServicioJwt jwt;
    private final HandlerExceptionResolver resolutorExcepciones;

    public FiltroJwtTienda(ServicioJwt jwt,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolutorExcepciones) {
        this.jwt = jwt;
        this.resolutorExcepciones = resolutorExcepciones;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        String uri = peticion.getRequestURI();
        return !uri.startsWith(ConfiguracionSeguridad.RAIZ_CUENTA)
                || ConfiguracionSeguridad.esRutaDeSesionDeCuenta(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera == null || !cabecera.startsWith(PREFIJO)) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        try {
            ServicioJwt.ContenidoTokenCliente contenido =
                    jwt.validarTienda(cabecera.substring(PREFIJO.length()).trim());
            var principal = new ClienteAutenticado(contenido.idCliente(), contenido.email(), contenido.nombre());
            var autenticacion = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(ClienteAutenticado.AUTORIDAD)));
            autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(peticion));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        } catch (ExcepcionAplicacion ex) {
            // Sin limpiar el contexto, un token inválido podría dejar viva una
            // autenticación de otra petición del mismo hilo.
            SecurityContextHolder.clearContext();
            resolutorExcepciones.resolveException(peticion, respuesta, null, ex);
            return;
        }

        cadena.doFilter(peticion, respuesta);
    }
}
