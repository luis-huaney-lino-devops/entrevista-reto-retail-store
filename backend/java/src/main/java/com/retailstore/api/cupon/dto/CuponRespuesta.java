package com.retailstore.api.cupon.dto;

import com.retailstore.api.cupon.dominio.Cupon;
import java.math.BigDecimal;
import java.time.Instant;

public record CuponRespuesta(
        Long id,
        String codigo,
        String tipo,
        BigDecimal valor,
        BigDecimal subtotalMinimo,
        Instant iniciaEn,
        Instant terminaEn,
        Integer usosMaximos,
        int usosActuales,
        boolean activo,
        boolean vigente) {

    public static CuponRespuesta de(Cupon cupon, Instant ahora) {
        return new CuponRespuesta(
                cupon.getId(),
                cupon.getCodigo(),
                cupon.getTipo().name(),
                cupon.getValor(),
                cupon.getSubtotalMinimo(),
                cupon.getIniciaEn(),
                cupon.getTerminaEn(),
                cupon.getUsosMaximos(),
                cupon.getUsosActuales(),
                cupon.isActivo(),
                cupon.estaVigente(ahora) && cupon.quedanUsos());
    }
}
