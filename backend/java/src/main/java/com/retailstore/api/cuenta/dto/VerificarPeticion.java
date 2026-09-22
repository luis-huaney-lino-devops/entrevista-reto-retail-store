package com.retailstore.api.cuenta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El token del correo de verificación. */
public record VerificarPeticion(

        @NotBlank(message = "es obligatorio")
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String token) {
}
