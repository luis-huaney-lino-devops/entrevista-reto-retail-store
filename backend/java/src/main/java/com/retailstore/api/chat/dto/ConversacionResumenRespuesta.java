package com.retailstore.api.chat.dto;

import com.retailstore.api.chat.dominio.Conversacion;
import java.time.Instant;

public record ConversacionResumenRespuesta(
        Long id,
        String asunto,
        String estado,
        Long clienteId,
        String clienteNombre,
        String clienteEmail,
        Long ordenId,
        String ordenNumero,
        int noLeidos,
        Instant ultimoMensajeEn,
        Instant creadoEn) {

    public static ConversacionResumenRespuesta de(Conversacion conversacion) {
        var orden = conversacion.getOrden();
        return new ConversacionResumenRespuesta(
                conversacion.getId(),
                conversacion.getAsunto(),
                conversacion.getEstado().name(),
                conversacion.getCliente().getId(),
                conversacion.getCliente().getNombre(),
                conversacion.getCliente().getEmail(),
                orden == null ? null : orden.getId(),
                orden == null ? null : orden.getNumero(),
                conversacion.getNoLeidosAdmin(),
                conversacion.getUltimoMensajeEn(),
                conversacion.getCreadoEn());
    }
}
