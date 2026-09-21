package com.retailstore.api.orden.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CambiarEstadoOrdenPeticion(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "PENDIENTE|PAGADA|ENVIADA|ENTREGADA|CANCELADA",
                message = "no es un estado válido")
        String estado) {
}
