package com.retailstore.api.catalogo.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Edición de producto.
 *
 * <p><strong>No lleva SKU</strong> (RN-002): es inmutable tras la creación. Si
 * el cuerpo lo trajera habría que decidir qué hacer cuando difiere, y cualquier
 * respuesta que no sea rechazar es una forma de permitirlo a medias.
 */
public record ActualizarProductoPeticion(
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

        List<Long> imagenIds) {
}
