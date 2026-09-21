package com.retailstore.api.catalogo.dto;

/** Referencia mínima a otra entidad: lo que un listado necesita para enlazar. */
public record ReferenciaRespuesta(Long id, String nombre, String slug) {
}
