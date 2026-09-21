package com.retailstore.api.notificacion.dto;

import com.retailstore.api.notificacion.dominio.Notificacion;
import java.time.Instant;

public record NotificacionRespuesta(
        Long id,
        String tipo,
        String severidad,
        String titulo,
        String detalle,
        String enlace,
        boolean leida,
        Instant creadoEn) {

    public static NotificacionRespuesta de(Notificacion notificacion) {
        return new NotificacionRespuesta(
                notificacion.getId(),
                notificacion.getTipo().name(),
                notificacion.getSeveridad().name(),
                notificacion.getTitulo(),
                notificacion.getDetalle(),
                notificacion.getEnlace(),
                notificacion.estaLeida(),
                notificacion.getCreadoEn());
    }
}
