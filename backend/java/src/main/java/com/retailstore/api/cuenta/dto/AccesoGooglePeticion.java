package com.retailstore.api.cuenta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El ID token que devuelve Google Identity Services en el navegador.
 *
 * <p>Se llama {@code credencial} porque eso es lo que entrega Google
 * ({@code response.credential}), y llega tal cual: quien lo valida es el
 * backend contra el JWKS de Google, no el frontend.
 */
public record AccesoGooglePeticion(
        @NotBlank(message = "es obligatoria")
        @Size(max = 4000, message = "no puede superar 4000 caracteres")
        String credencial) {
}
