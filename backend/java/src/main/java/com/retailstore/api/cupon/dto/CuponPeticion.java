package com.retailstore.api.cupon.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Alta y edición comparten forma: un cupón no tiene campos inmutables salvo el
 * código, que en la edición se ignora.
 */
public record CuponPeticion(
        @NotBlank(message = "es obligatorio")
        @Size(max = 40, message = "no puede superar 40 caracteres")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "solo admite letras, dígitos, punto, guion y guion bajo")
        String codigo,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "PORCENTAJE|MONTO_FIJO", message = "debe ser PORCENTAJE o MONTO_FIJO")
        String tipo,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 10, fraction = 2, message = "admite como máximo dos decimales")
        BigDecimal valor,

        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "admite como máximo dos decimales")
        BigDecimal subtotalMinimo,

        @NotNull(message = "es obligatoria")
        Instant iniciaEn,

        @NotNull(message = "es obligatoria")
        Instant terminaEn,

        /** Nulo significa usos ilimitados. */
        @Positive(message = "debe ser mayor que cero")
        Integer usosMaximos,

        boolean activo) {
}
