package com.retailstore.api.catalogo.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Alta de producto. Nace <strong>inactivo</strong>: se crea como borrador y se
 * publica con un PATCH aparte, cuando ya tiene fotos y precio revisado.
 */
public record CrearProductoPeticion(
        @NotBlank(message = "es obligatorio")
        @Size(max = 40, message = "no puede superar 40 caracteres")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "solo admite letras, dígitos, punto, guion y guion bajo")
        String sku,

        @NotBlank(message = "es obligatorio")
        @Size(max = 160, message = "no puede superar 160 caracteres")
        String nombre,

        @Size(max = 300, message = "no puede superar 300 caracteres")
        String descripcionCorta,

        @Size(max = 5000, message = "no puede superar 5000 caracteres")
        String descripcion,

        @NotNull(message = "es obligatoria")
        Long subcategoriaId,

        Long marcaId,

        @NotNull(message = "es obligatorio")
        @Digits(integer = 10, fraction = 2, message = "admite como máximo dos decimales")
        BigDecimal precio,

        @Digits(integer = 10, fraction = 2, message = "admite como máximo dos decimales")
        BigDecimal precioAnterior,

        @PositiveOrZero(message = "no puede ser negativo")
        int stock,

        boolean destacado,

        /** Archivos ya subidos, en el orden en que se mostrarán. */
        List<Long> imagenIds) {
}
