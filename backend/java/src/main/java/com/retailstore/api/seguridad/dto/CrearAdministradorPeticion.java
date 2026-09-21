package com.retailstore.api.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearAdministradorPeticion(
        @NotBlank(message = "es obligatorio")
        @Size(min = 3, max = 60, message = "debe tener entre 3 y 60 caracteres")
        @Pattern(regexp = "[a-z0-9._-]+", message = "solo admite minúsculas, dígitos, punto, guion y guion bajo")
        String usuario,

        @NotBlank(message = "es obligatoria")
        String contrasena,

        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String nombre,

        @Pattern(regexp = "ADMINISTRADOR|SUPERADMINISTRADOR", message = "debe ser ADMINISTRADOR o SUPERADMINISTRADOR")
        String rol) {
}
