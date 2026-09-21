package com.retailstore.api.catalogo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * Filtros del listado (RN-022, RN-023, RN-024).
 *
 * <p>Los valores por omisión están aquí y no en el controlador para que el
 * mismo objeto sirva a la tienda y al panel sin repetirlos.
 *
 * @param pagina       1-based
 * @param tamanoPagina entre 1 y 48; por omisión 12
 */
public record BusquedaProductoPeticion(
        String texto,
        String categoria,
        String subcategoria,
        String marca,
        BigDecimal precioMinimo,
        BigDecimal precioMaximo,
        Boolean conStock,
        Boolean destacado,
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
        return tamanoPagina == null ? 12 : tamanoPagina;
    }
}
