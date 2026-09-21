package com.retailstore.api.catalogo.dto;

/**
 * Destacar o quitar de destacados.
 *
 * <p>Endpoint propio por el mismo motivo que {@link CambiarEstadoPeticion}: es
 * una decisión de escaparate, no una corrección del producto, y desde la lista
 * se hace de un clic sin tener que abrir el formulario entero —que además
 * obligaría a reenviar la descripción y la galería para cambiar un booleano.
 */
public record CambiarDestacadoPeticion(boolean destacado) {
}
