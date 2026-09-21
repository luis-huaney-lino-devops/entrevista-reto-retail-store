package com.retailstore.api.cuenta.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Alta y edición de una dirección. Misma forma para las dos: una dirección no
 * tiene campos que solo se puedan fijar al crearla.
 *
 * <p>Solo llega el {@code distritoId}. La provincia y el departamento se
 * deducen de él -el ubigeo es un árbol- y aceptarlos del cliente permitiría
 * guardar una combinación imposible.
 *
 * <p>Los rangos de latitud y longitud están además como CHECK en la base. Aquí
 * sirven para que el error sea un {@code 400} con el campo señalado y no un
 * {@code 500} traducido de PostgreSQL.
 */
public record DireccionPeticion(
        @NotNull(message = "es obligatorio")
        Integer distritoId,

        @NotBlank(message = "es obligatoria")
        @Size(max = 40, message = "no puede superar 40 caracteres")
        String etiqueta,

        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar 120 caracteres")
        String destinatario,

        @NotBlank(message = "es obligatorio")
        @Size(max = 20, message = "no puede superar 20 caracteres")
        String telefono,

        @NotBlank(message = "es obligatoria")
        @Size(max = 200, message = "no puede superar 200 caracteres")
        String calle,

        @Size(max = 20, message = "no puede superar 20 caracteres")
        String numero,

        @Size(max = 300, message = "no puede superar 300 caracteres")
        String referencia,

        @Size(max = 12, message = "no puede superar 12 caracteres")
        String codigoPostal,

        @DecimalMin(value = "-90", message = "debe estar entre -90 y 90")
        @DecimalMax(value = "90", message = "debe estar entre -90 y 90")
        BigDecimal latitud,

        @DecimalMin(value = "-180", message = "debe estar entre -180 y 180")
        @DecimalMax(value = "180", message = "debe estar entre -180 y 180")
        BigDecimal longitud,

        boolean predeterminada) {
}
