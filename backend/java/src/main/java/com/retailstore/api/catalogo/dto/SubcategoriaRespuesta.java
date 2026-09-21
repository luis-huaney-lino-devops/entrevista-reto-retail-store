package com.retailstore.api.catalogo.dto;

import com.retailstore.api.archivo.dto.ArchivoRespuesta;
import com.retailstore.api.catalogo.dominio.Subcategoria;

public record SubcategoriaRespuesta(
        Long id,
        String nombre,
        String slug,
        String descripcion,
        ArchivoRespuesta imagen,
        int orden,
        boolean activa,
        ReferenciaRespuesta categoria) {

    public static SubcategoriaRespuesta de(Subcategoria subcategoria) {
        var categoria = subcategoria.getCategoria();
        return new SubcategoriaRespuesta(
                subcategoria.getId(),
                subcategoria.getNombre(),
                subcategoria.getSlug(),
                subcategoria.getDescripcion(),
                subcategoria.getImagen() == null ? null : ArchivoRespuesta.de(subcategoria.getImagen()),
                subcategoria.getOrden(),
                subcategoria.isActiva(),
                new ReferenciaRespuesta(categoria.getId(), categoria.getNombre(), categoria.getSlug()));
    }
}
