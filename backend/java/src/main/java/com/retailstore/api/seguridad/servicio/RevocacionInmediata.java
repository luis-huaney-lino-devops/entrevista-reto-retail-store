package com.retailstore.api.seguridad.servicio;

import com.retailstore.api.seguridad.repositorio.TokenRefrescoAdminRepositorio;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revocación que sobrevive al rechazo de la petición.
 *
 * <p>El caso es sutil y grave: al detectar la reutilización de un token de
 * refresco hay que hacer dos cosas -revocar la familia y rechazar la petición-.
 * Si ambas ocurren en la misma transacción, la excepción que produce el rechazo
 * <strong>deshace la revocación</strong>. El atacante recibe un 401 y la sesión
 * robada sigue viva: exactamente lo contrario de lo que se pretendía.
 *
 * <p>Por eso esto es un bean aparte y no un método privado: {@code REQUIRES_NEW}
 * solo se aplica cuando la llamada pasa por el proxy de Spring, y una llamada a
 * un método propio no pasa por él.
 */
@Component
public class RevocacionInmediata {

    private final TokenRefrescoAdminRepositorio tokens;

    public RevocacionInmediata(TokenRefrescoAdminRepositorio tokens) {
        this.tokens = tokens;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revocarFamilia(UUID familia, Instant momento) {
        tokens.revocarFamilia(familia, momento);
    }
}
