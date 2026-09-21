package com.retailstore.api.seguridad.config;

import com.retailstore.api.comun.error.ExcepcionAplicacion;
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
 * Traduce el {@code Authorization: Bearer} del panel a una autenticación de
 * Spring Security.
 *
 * <p>Si no hay cabecera, el filtro no hace nada y deja pasar: decidir si esa
 * ruta exige autenticación es trabajo de las reglas de autorización, no suyo.
 * Un filtro que rechaza por su cuenta acaba duplicando -y contradiciendo- esas
 * reglas.
 *
 * <p>Los fallos se delegan al {@link HandlerExceptionResolver} para que salgan
 * por {@code ManejadorGlobalErrores}. Escribir el JSON a mano aquí produciría
 * un segundo formato de error, parecido pero distinto, que los clientes tendrían
 * que aprender por separado.
 */
@Component
public class FiltroJwtPanel extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final ServicioJwt jwt;
    private final HandlerExceptionResolver resolutorExcepciones;

    public FiltroJwtPanel(ServicioJwt jwt,
                          @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolutorExcepciones) {
        this.jwt = jwt;
        this.resolutorExcepciones = resolutorExcepciones;
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
            ServicioJwt.ContenidoToken contenido = jwt.validarPanel(cabecera.substring(PREFIJO.length()).trim());
            var autenticacion = new UsernamePasswordAuthenticationToken(
                    contenido.usuario(),
                    null,
                    List.of(new SimpleGrantedAuthority(contenido.rol().autoridad())));
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
