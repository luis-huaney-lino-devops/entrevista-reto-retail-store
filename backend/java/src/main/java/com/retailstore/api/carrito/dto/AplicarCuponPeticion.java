package com.retailstore.api.carrito.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AplicarCuponPeticion(
        @NotBlank(message = "es obligatorio")
        @Size(max = 40, message = "no puede superar 40 caracteres")
        String codigo) {
}
