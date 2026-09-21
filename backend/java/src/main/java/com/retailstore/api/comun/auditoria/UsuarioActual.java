package com.retailstore.api.comun.auditoria;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Quién está haciendo la petición.
 *
 * <p>La auditoría automática de Spring Data cubre {@code creadoPor} y
 * {@code actualizadoPor}, pero la eliminación lógica escribe una columna que
 * no tiene anotación equivalente: hay que poner el autor a mano. Esta clase es
 * la única fuente de esa respuesta, para que el nombre que se guarda al
 * eliminar sea exactamente el mismo que el que se guarda al modificar.
 */
public final class UsuarioActual {

    /** Lo que se registra cuando el cambio no viene de una sesión del panel. */
    public static final String SISTEMA = "sistema";

    private UsuarioActual() {
    }

    public static String nombre() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .filter(usuario -> !"anonymousUser".equals(usuario))
                .orElse(SISTEMA);
    }
}
