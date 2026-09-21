package com.retailstore.api.catalogo.dto;

/**
 * Publicar o despublicar. Endpoint propio y no un campo más del PUT: cambiar el
 * estado es la operación de negocio, y mezclarla con la edición hace imposible
 * distinguir en el registro de auditoría quién publicó de quién corrigió una
 * falta de ortografía.
 */
public record CambiarEstadoPeticion(boolean activo) {
}
