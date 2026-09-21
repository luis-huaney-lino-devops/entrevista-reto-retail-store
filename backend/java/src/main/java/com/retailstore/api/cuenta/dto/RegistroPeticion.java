package com.retailstore.api.cuenta.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de cuenta con correo y contraseña.
 *
 * <p>La contraseña solo se valida aquí en longitud máxima; el mínimo y la lista
 * de bloqueo los aplica {@code PoliticaContrasena} (RN-062), que es el único
 * sitio donde esa política está escrita.
 */
public record RegistroPeticion(
        @NotBlank(message = "es obligatorio")
        @Email(message = "no es un correo válido")
        @Size(max = 160, message = "no puede superar 160 caracteres")
        String email,

        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String nombre,

        @NotBlank(message = "es obligatoria")
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String contrasena,

        @Size(max = 20, message = "no puede superar 20 caracteres")
        String telefono) {
}
