package com.retailstore.api.carrito.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * El carrito completo. Toda mutación devuelve esto (RN-033): el cliente nunca
 * tiene que recalcular totales ni pedir el carrito otra vez, y así no puede
 * mostrar un total distinto del que cobrará el servidor.
 *
 * @param cuponAplicado nulo si no hay cupón
 * @param cuponActivo   false cuando hay cupón pero ahora mismo no descuenta
 *                      -por ejemplo, el subtotal bajó del mínimo (RN-045)
 */
public record CarritoRespuesta(
        UUID id,
        List<ItemCarritoRespuesta> items,
        int totalUnidades,
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal total,
        String cuponAplicado,
        boolean cuponActivo,
        String motivoCuponInactivo) {
}
