package com.retailstore.api.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cambio de contraseña propia.
 *
 * <p>Pide la actual aunque haya una sesión válida: si alguien deja el panel
 * abierto un minuto, lo que se evita es que quien pase por ahí se apropie de la
 * cuenta cambiándole la contraseña.
 */
public record CambiarContrasenaPeticion(
        @NotBlank(message = "es obligatoria") String contrasenaActual,
        @NotBlank(message = "es obligatoria") String contrasenaNueva) {
}
