package com.retailstore.api.carrito.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AgregarItemPeticion(
        @NotNull(message = "es obligatorio")
        Long productoId,

        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = 99, message = "no puede superar 99")
        int cantidad) {
}
