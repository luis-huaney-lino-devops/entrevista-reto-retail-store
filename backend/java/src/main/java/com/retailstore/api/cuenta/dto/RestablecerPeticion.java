package com.retailstore.api.cuenta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El token del correo y la contraseña nueva.
 *
 * <p>No lleva el correo a propósito: el token ya identifica la cuenta. Si lo
 * llevara, habría que comprobar que ambos coinciden, y esa comprobación es un
 * sitio más donde equivocarse.
 */
public record RestablecerPeticion(

        @NotBlank(message = "es obligatorio")
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String token,

        @NotBlank(message = "es obligatoria")
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String contrasenaNueva) {
}
