package com.retailstore.api.carrito.servicio;

import com.retailstore.api.carrito.dominio.Carrito;
import com.retailstore.api.carrito.dominio.ItemCarrito;
import com.retailstore.api.cupon.dominio.Cupon;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Los importes del carrito, calculados siempre en el servidor.
 *
 * <p>RN-051: el servidor jamás acepta importes del cliente. Ningún DTO de
 * entrada los lleva, y por eso no hay forma de enviarlos aunque se quiera.
 */
@Component
public class CalculadoraCarrito {

    private final Clock reloj;

    public CalculadoraCarrito(Clock reloj) {
        this.reloj = reloj;
    }

    public PrecioCarrito calcular(Carrito carrito) {
        BigDecimal subtotal = carrito.getItems().stream()
                .map(ItemCarrito::totalLinea)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Cupon cupon = carrito.getCupon();
        if (cupon == null) {
            return new PrecioCarrito(subtotal, BigDecimal.ZERO.setScale(2), subtotal, false, null);
        }

        // RN-045: un cupón que deja de aplicar se ignora pero sigue vinculado.
        // Si el comprador quita productos y baja del mínimo, el descuento
        // desaparece; al volver a agregar, reaparece solo. Desvincularlo lo
        // obligaría a reescribir el código por una acción que él mismo puede
        // deshacer en el siguiente clic.
        String motivo = motivoParaNoAplicar(cupon, subtotal);
        if (motivo != null) {
            return new PrecioCarrito(subtotal, BigDecimal.ZERO.setScale(2), subtotal, false, motivo);
        }

        BigDecimal descuento = cupon.descuentoPara(subtotal).setScale(2, RoundingMode.HALF_UP);
        return new PrecioCarrito(subtotal, descuento, subtotal.subtract(descuento), true, null);
    }

    private String motivoParaNoAplicar(Cupon cupon, BigDecimal subtotal) {
        if (!cupon.estaVigente(reloj.instant())) {
            return "El cupón ya no está vigente.";
        }
        if (!cupon.quedanUsos()) {
            return "El cupón agotó sus usos.";
        }
        if (!cupon.alcanzaMinimo(subtotal)) {
            return "El cupón requiere una compra mínima de S/ " + cupon.getSubtotalMinimo() + ".";
        }
        return null;
    }
}
