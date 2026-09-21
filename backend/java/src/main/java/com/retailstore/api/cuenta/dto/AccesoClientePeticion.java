package com.retailstore.api.cuenta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de la tienda.
 *
 * <p>Sin {@code @Email}: un correo mal escrito tiene que devolver el mismo
 * {@code 401} que uno que no existe (RN-067). Un {@code 400} distinguible
 * convertiría el endpoint en un validador de direcciones.
 */
public record AccesoClientePeticion(
        @NotBlank(message = "es obligatorio")
        @Size(max = 160, message = "no puede superar 160 caracteres")
        String email,

        @NotBlank(message = "es obligatoria")
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String contrasena) {
}
