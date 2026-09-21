package com.retailstore.api.chat.dto;

import com.retailstore.api.chat.dominio.Adjunto;
import com.retailstore.api.chat.dominio.Mensaje;
import java.time.Instant;
import java.util.List;

public record MensajeRespuesta(
        Long id,
        String autor,
        String autorNombre,
        String cuerpo,
        List<AdjuntoRespuesta> adjuntos,
        Instant enviadoEn,
        Instant leidoEn) {

    public record AdjuntoRespuesta(Long id, String nombre, String tipoMime, long bytes, String url,
                                   boolean esImagen) {
        static AdjuntoRespuesta de(Adjunto adjunto) {
            return new AdjuntoRespuesta(
                    adjunto.getId(),
                    adjunto.getNombreOriginal(),
                    adjunto.getTipoMime(),
                    adjunto.getBytes(),
                    adjunto.getUrlPublica(),
                    adjunto.esImagen());
        }
    }

    public static MensajeRespuesta de(Mensaje mensaje) {
        return new MensajeRespuesta(
                mensaje.getId(),
                mensaje.getAutor().name(),
                mensaje.getAutorNombre(),
                mensaje.getCuerpo(),
                mensaje.getAdjuntos().stream().map(AdjuntoRespuesta::de).toList(),
                mensaje.getEnviadoEn(),
                mensaje.getLeidoEn());
    }
}
