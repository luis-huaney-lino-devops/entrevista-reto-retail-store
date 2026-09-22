package com.retailstore.api.orden.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Lo que envía el checkout de la tienda.
 *
 * <p><strong>Aquí no hay un solo importe, y es deliberado</strong> (RN-051).
 * El servidor recalcula subtotal, descuento y total desde el carrito y los
 * precios vigentes. Al no existir el campo, no hay forma de mandar un total
 * aunque se quiera: la regla no depende de que alguien recuerde validarla.
 *
 * <p>Tampoco lleva las líneas. El carrito ya está en el servidor
 * (ADR-0002) y se identifica por {@code carritoId}; aceptar aquí una lista de
 * productos sería abrir una segunda vía para decidir qué se compra.
 */
public record CrearOrdenPeticion(

        @NotNull(message = "es obligatorio")
        UUID carritoId,

        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String nombreContacto,

        /**
         * Obligatorio aunque se compre sin cuenta (RN-056): es la única vía
         * para confirmarle el pedido a quien lo hizo.
         */
        @NotBlank(message = "es obligatorio")
        @Email(message = "no tiene formato de correo")
        @Size(max = 160, message = "no puede superar 160 caracteres")
        String email,

        @Size(max = 20, message = "no puede superar 20 caracteres")
        String telefono,

        @NotBlank(message = "es obligatorio")
        @Size(max = 300, message = "no puede superar 300 caracteres")
        String direccion) {
}
