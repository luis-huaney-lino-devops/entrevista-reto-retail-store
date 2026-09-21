package com.retailstore.api.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccesoPeticion(
        @NotBlank(message = "es obligatorio")
        @Size(max = 60, message = "no puede superar 60 caracteres")
        String usuario,

        @NotBlank(message = "es obligatoria")
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String contrasena) {
}
