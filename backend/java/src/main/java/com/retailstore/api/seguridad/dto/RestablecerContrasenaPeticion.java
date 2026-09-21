package com.retailstore.api.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

/** Restablecimiento hecho por un superadministrador. No pide la contraseña actual. */
public record RestablecerContrasenaPeticion(@NotBlank(message = "es obligatoria") String contrasenaNueva) {
}
