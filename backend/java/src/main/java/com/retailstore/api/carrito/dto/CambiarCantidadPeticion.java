package com.retailstore.api.carrito.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Cantidad entre 1 y 99 (RN-034). Cero no es una cantidad: para quitar la línea
 * está {@code DELETE}. Aceptar cero como «eliminar» mezcla dos operaciones con
 * consecuencias distintas en un mismo endpoint.
 */
public record CambiarCantidadPeticion(
        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = 99, message = "no puede superar 99")
        int cantidad) {
}
