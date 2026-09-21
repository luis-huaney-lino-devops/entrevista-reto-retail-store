package com.retailstore.api.cuenta.servicio;

import com.retailstore.api.cuenta.repositorio.TokenRefrescoClienteRepositorio;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revocación que sobrevive al rechazo de la petición, para la tienda.
 *
 * <p>Es el gemelo de {@code RevocacionInmediata} del panel, y existe por el
 * mismo motivo, que ya costó una sesión entera: al detectar la reutilización de
 * un refresco hay que revocar la familia <strong>y</strong> rechazar la
 * petición, y si ambas cosas ocurren en la misma transacción, la excepción del
 * rechazo deshace la revocación. El atacante recibe un 401 y la sesión robada
 * sigue viva.
 *
 * <p>Bean aparte y no método privado: {@code REQUIRES_NEW} solo se aplica
 * cuando la llamada pasa por el proxy de Spring, y llamarse a uno mismo no
 * pasa por él.
 */
@Component
public class RevocacionInmediataCliente {

    private final TokenRefrescoClienteRepositorio tokens;

    public RevocacionInmediataCliente(TokenRefrescoClienteRepositorio tokens) {
        this.tokens = tokens;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revocarFamilia(UUID familia, Instant momento) {
        tokens.revocarFamilia(familia, momento);
    }
}
