package com.retailstore.api.opinion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Edición de la opinión propia. Sin {@code productoId}: una opinión no se
 * muda de producto. Cambiarlo esquivaría el «una por persona y producto»
 * (RN-090) y dejaría el promedio del producto de origen sin recalcular.
 */
public record ActualizarOpinionPeticion(
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
