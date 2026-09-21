package com.retailstore.api.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** El usuario no se puede cambiar: es la identidad con la que se accede. */
public record ActualizarAdministradorPeticion(
        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String nombre,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "ADMINISTRADOR|SUPERADMINISTRADOR", message = "debe ser ADMINISTRADOR o SUPERADMINISTRADOR")
        String rol,

        boolean activo) {
}
