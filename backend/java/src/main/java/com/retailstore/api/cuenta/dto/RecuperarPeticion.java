package com.retailstore.api.cuenta.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A qué dirección mandar el enlace. */
public record RecuperarPeticion(

        @NotBlank(message = "es obligatorio")
        @Email(message = "no tiene formato de correo")
        @Size(max = 160, message = "no puede superar 160 caracteres")
        String email) {
}
