package com.retailstore.api.correo.servicio;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Manda el correo <strong>solo si la transacción confirma</strong> (RN-083).
 *
 * <p>Enviarlo dentro tiene un fallo que no se ve hasta que pasa: si algo revienta
 * después, la transacción deshace la fila pero <em>el correo ya salió</em>. Queda
 * alguien con un mensaje de bienvenida a una cuenta que no existe, o —peor— con
 * un enlace de recuperación cuyo token se deshizo y que no va a funcionar.
 *
 * <p>No se puede deshacer un correo. Por eso se espera al commit.
 *
 * <p>Si no hay transacción activa —una llamada desde fuera de un servicio
 * transaccional— se envía en el momento: no hay commit que esperar.
 */
@Component
public class CorreoTrasCommit {

    private final ServicioCorreo correo;

    public CorreoTrasCommit(ServicioCorreo correo) {
        this.correo = correo;
    }

    public void bienvenida(String destinatario, String nombre) {
        alConfirmar(() -> correo.bienvenida(destinatario, nombre));
    }

    public void recuperacion(String destinatario, String nombre, String token) {
        alConfirmar(() -> correo.recuperacion(destinatario, nombre, token));
    }

    public void intentoDeRegistroDuplicado(String destinatario, String nombre) {
        alConfirmar(() -> correo.intentoDeRegistroDuplicado(destinatario, nombre));
    }

    private void alConfirmar(Runnable envio) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
            return;
        }
        envio.run();
    }
}
