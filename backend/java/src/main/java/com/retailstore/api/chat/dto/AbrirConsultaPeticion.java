package com.retailstore.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que manda el cliente al abrir una consulta desde la tienda.
 *
 * <p>No lleva {@code clienteId}, a diferencia de {@link AbrirConversacionPeticion}
 * —que la usa el panel para abrir un hilo <em>a</em> alguien—. Aquí el cliente
 * sale del token de sesión: aceptarlo en el cuerpo permitiría abrir una
 * conversación a nombre de otra persona.
 *
 * @param ordenId sobre qué compra es la consulta. Puede ser nulo: también se
 *                pregunta por cosas que no son un pedido. Si viene, se comprueba
 *                que la orden sea suya.
 */
public record AbrirConsultaPeticion(

        Long ordenId,

        @NotBlank(message = "es obligatorio")
        @Size(max = 160, message = "no puede superar 160 caracteres")
        String asunto,

        @Size(max = 4000, message = "no puede superar 4000 caracteres")
        String mensaje) {
}
