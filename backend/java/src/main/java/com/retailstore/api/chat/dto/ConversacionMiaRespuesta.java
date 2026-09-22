package com.retailstore.api.chat.dto;

import com.retailstore.api.chat.dominio.Conversacion;
import java.time.Instant;

/**
 * Una conversación en la bandeja del <strong>cliente</strong>.
 *
 * <p>DTO propio y no {@link ConversacionResumenRespuesta}, que es el del panel,
 * porque son dos vistas distintas de la misma fila:
 *
 * <ul>
 *   <li>El contador que importa aquí es {@code noLeidosCliente} —las respuestas
 *       del administrador sin ver—, no el del panel.</li>
 *   <li>Lleva el {@code token}, que es con lo que la tienda abre el hilo.</li>
 *   <li><strong>No lleva el nombre ni el correo del cliente.</strong> El panel
 *       los necesita para saber con quién habla; a quien mira su propia bandeja
 *       no le aportan nada, y un DTO que los arrastra es un DTO que un día
 *       acaba sirviéndose donde no toca.</li>
 * </ul>
 */
public record ConversacionMiaRespuesta(
        Long id,
        String token,
        String asunto,
        String estado,
        Long ordenId,
        String ordenNumero,
        int noLeidos,
        Instant ultimoMensajeEn,
        Instant creadoEn) {

    public static ConversacionMiaRespuesta de(Conversacion conversacion) {
        var orden = conversacion.getOrden();
        return new ConversacionMiaRespuesta(
                conversacion.getId(),
                conversacion.getTokenAcceso().toString(),
                conversacion.getAsunto(),
                conversacion.getEstado().name(),
                orden == null ? null : orden.getId(),
                orden == null ? null : orden.getNumero(),
                conversacion.getNoLeidosCliente(),
                conversacion.getUltimoMensajeEn(),
                conversacion.getCreadoEn());
    }
}
