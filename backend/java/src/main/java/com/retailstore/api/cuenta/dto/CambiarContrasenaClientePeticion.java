package com.retailstore.api.cuenta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambiar o establecer la contraseña.
 *
 * <p>{@code contrasenaActual} es opcional en el DTO y obligatoria en el
 * servicio <strong>salvo</strong> que la cuenta no tenga contraseña todavía
 * (entró con Google). Pedirle «la actual» a quien nunca tuvo una es un
 * formulario que no se puede completar, y RN-065 lo que exige es que nadie
 * acabe sin forma de entrar, no que todo el mundo teclee una contraseña previa.
 *
 * <p>La validación de fuerza de {@code contrasenaNueva} la hace
 * {@code PoliticaContrasena}, no una anotación: la lista de bloqueo no cabe en
 * un {@code @Pattern}.
 */
public record CambiarContrasenaClientePeticion(
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String contrasenaActual,

        @NotBlank(message = "es obligatoria")
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String contrasenaNueva) {
}
