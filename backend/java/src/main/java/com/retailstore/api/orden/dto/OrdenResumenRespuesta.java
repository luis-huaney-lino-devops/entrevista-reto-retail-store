package com.retailstore.api.orden.dto;

import com.retailstore.api.orden.dominio.Orden;
import java.math.BigDecimal;
import java.time.Instant;

public record OrdenResumenRespuesta(
        Long id,
        String numero,
        String nombreContacto,
        String email,
        BigDecimal total,
        String estado,
        String estadoEtiqueta,
        int totalUnidades,
        Instant creadoEn) {

    public static OrdenResumenRespuesta de(Orden orden) {
        return new OrdenResumenRespuesta(
                orden.getId(),
                orden.getNumero(),
                orden.getNombreContacto(),
                orden.getEmail(),
                orden.getTotal(),
                orden.getEstado().name(),
                orden.getEstado().etiqueta(),
                orden.totalUnidades(),
                orden.getCreadoEn());
    }
}
