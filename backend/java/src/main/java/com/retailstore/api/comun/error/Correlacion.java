package com.retailstore.api.comun.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Identificador de correlación por petición (RN-081).
 *
 * <p>Se pone en el MDC, en la cabecera de respuesta y en el cuerpo de todo
 * error. Es lo que convierte un reporte de usuario -una cadena opaca- en una
 * línea de log concreta.
 *
 * <p>Se acepta el valor que llegue en {@code X-Correlation-Id} para poder seguir
 * una petición que atravesó Caddy o el frontend, pero se sanea: un cliente puede
 * enviar cualquier cosa, y esa cadena termina en los logs.
 *
 * <p>El filtro va de los primeros: un error en cualquier filtro posterior debe
 * salir ya con identificador.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class Correlacion extends OncePerRequestFilter {

    public static final String CABECERA = "X-Correlation-Id";
    public static final String CLAVE_MDC = "correlationId";

    private static final int LARGO_MAXIMO = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String identificador = sanear(peticion.getHeader(CABECERA));
        MDC.put(CLAVE_MDC, identificador);
        respuesta.setHeader(CABECERA, identificador);
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            // Sin esto el hilo del pool arrastra el identificador de la
            // petición anterior y los logs mienten.
            MDC.remove(CLAVE_MDC);
        }
    }

    /** El identificador de la petición en curso, o uno nuevo fuera de una petición. */
    public static String actual() {
        String identificador = MDC.get(CLAVE_MDC);
        return identificador != null ? identificador : UUID.randomUUID().toString();
    }

    private static String sanear(String recibido) {
        if (recibido == null || recibido.isBlank() || recibido.length() > LARGO_MAXIMO) {
            return UUID.randomUUID().toString();
        }
        for (int i = 0; i < recibido.length(); i++) {
            char c = recibido.charAt(i);
            boolean admitido = Character.isLetterOrDigit(c) || c == '-' || c == '_';
            if (!admitido) {
                return UUID.randomUUID().toString();
            }
        }
        return recibido;
    }
}
