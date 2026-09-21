package com.retailstore.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AbrirConversacionPeticion(
        @NotNull(message = "es obligatorio")
        Long clienteId,

        Long ordenId,

        @NotBlank(message = "es obligatorio")
        @Size(max = 160, message = "no puede superar 160 caracteres")
        String asunto,

        @Size(max = 4000, message = "no puede superar 4000 caracteres")
        String mensaje) {
}
