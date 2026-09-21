package com.retailstore.api.carrito.servicio;

import java.math.BigDecimal;

/**
 * Los importes de un carrito, ya calculados.
 *
 * @param motivoCuponInactivo nulo cuando el cupón sí aplica, o cuando no hay
 *                            cupón. Es lo que permite a la tienda explicar por
 *                            qué el descuento no aparece
 */
public record PrecioCarrito(BigDecimal subtotal, BigDecimal descuento, BigDecimal total,
                            boolean cuponActivo, String motivoCuponInactivo) {
}
