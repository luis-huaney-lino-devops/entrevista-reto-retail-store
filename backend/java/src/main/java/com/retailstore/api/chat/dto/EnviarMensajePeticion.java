package com.retailstore.api.chat.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Un mensaje necesita cuerpo o adjuntos, pero no las dos cosas: mandar solo
 * una foto es normal. Que al menos haya una de las dos lo comprueba el
 * servicio, porque es una regla entre campos y no de un campo suelto.
 */
public record EnviarMensajePeticion(
        @Size(max = 4000, message = "no puede superar 4000 caracteres")
        String cuerpo,
        List<Long> adjuntoIds) {
}
