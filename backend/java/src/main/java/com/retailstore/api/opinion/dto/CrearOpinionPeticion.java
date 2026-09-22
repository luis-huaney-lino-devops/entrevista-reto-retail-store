package com.retailstore.api.opinion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de una opinión.
 *
 * <p>Lo que <strong>no</strong> está aquí es tan importante como lo que está:
 * ni el autor -sale del token (RN-091)- ni {@code compraVerificada} -la calcula
 * el servidor (RN-092)- ni la fecha. Aceptar cualquiera de los tres del cliente
 * sería dejar que se los invente.
 */
public record CrearOpinionPeticion(
        @NotNull(message = "es obligatorio")
        Long productoId,

        @NotNull(message = "es obligatoria")
        @Min(value = 1, message = "debe estar entre 1 y 5")
        @Max(value = 5, message = "debe estar entre 1 y 5")
        Integer calificacion,

        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String titulo,

        @NotBlank(message = "es obligatoria")
        @Size(max = 2000, message = "no puede superar 2000 caracteres")
        String cuerpo) {
}
