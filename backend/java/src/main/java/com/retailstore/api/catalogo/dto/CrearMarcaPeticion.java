package com.retailstore.api.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearMarcaPeticion(
        @NotBlank(message = "es obligatorio")
        @Size(max = 80, message = "no puede superar 80 caracteres")
        String nombre,

        @Size(max = 500, message = "no puede superar 500 caracteres")
        String descripcion,

        /** Archivo ya subido. Nulo si la marca no tiene logo. */
        Long logoId) {
}
