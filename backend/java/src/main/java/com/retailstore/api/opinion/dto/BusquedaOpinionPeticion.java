package com.retailstore.api.opinion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Paginación y orden del listado público de opiniones.
 *
 * <p>Mismos topes que el catálogo (RN-024): la página se rechaza, no se
 * recorta. Un cliente que pide 500 y recibe 48 sin enterarse cree que hay 48.
 *
 * @param pagina       1-based
 * @param tamanoPagina entre 1 y 48; por omisión 10, que es lo que cabe sin
 *                     empujar los relacionados fuera de la pantalla
 */
public record BusquedaOpinionPeticion(
        String orden,

        @Min(value = 1, message = "debe ser al menos 1")
        Integer pagina,

        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = 48, message = "no puede superar 48")
        Integer tamanoPagina) {

    public int paginaEfectiva() {
        return pagina == null ? 1 : pagina;
    }

    public int tamanoEfectivo() {
        return tamanoPagina == null ? 10 : tamanoPagina;
    }
}
