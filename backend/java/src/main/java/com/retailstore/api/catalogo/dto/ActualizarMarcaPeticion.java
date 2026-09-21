package com.retailstore.api.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El slug no se puede cambiar: es la identidad pública y es estable (RN-008). */
public record ActualizarMarcaPeticion(
        @NotBlank(message = "es obligatorio")
        @Size(max = 80, message = "no puede superar 80 caracteres")
        String nombre,

        @Size(max = 500, message = "no puede superar 500 caracteres")
        String descripcion,

        Long logoId,

        boolean activa) {
}
