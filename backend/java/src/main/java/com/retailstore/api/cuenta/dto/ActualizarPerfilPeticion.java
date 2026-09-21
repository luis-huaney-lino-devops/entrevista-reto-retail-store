package com.retailstore.api.cuenta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo único editable del perfil.
 *
 * <p>El correo no se puede cambiar por aquí: es la identidad de la cuenta
 * (RN-060), y admitir el cambio sin volver a verificarlo permitiría apuntar
 * una cuenta ajena a un buzón propio.
 */
public record ActualizarPerfilPeticion(
        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String nombre,

        @Size(max = 20, message = "no puede superar 20 caracteres")
        String telefono) {
}
