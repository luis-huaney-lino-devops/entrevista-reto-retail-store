package com.retailstore.api.catalogo.dto;

import com.retailstore.api.archivo.dto.ArchivoRespuesta;
import com.retailstore.api.catalogo.dominio.Categoria;
import java.util.List;

/**
 * @param subcategorias nulo cuando no se pidieron. Un arreglo vacío significa
 *                      «no tiene»; nulo significa «no se consultó», y son cosas
 *                      distintas que el cliente necesita distinguir
 */
public record CategoriaRespuesta(
        Long id,
        String nombre,
        String slug,
        String descripcion,
        ArchivoRespuesta imagen,
        int orden,
        boolean activa,
        List<SubcategoriaRespuesta> subcategorias) {

    public static CategoriaRespuesta de(Categoria categoria) {
        return de(categoria, null);
    }

    public static CategoriaRespuesta de(Categoria categoria, List<SubcategoriaRespuesta> subcategorias) {
        return new CategoriaRespuesta(
                categoria.getId(),
                categoria.getNombre(),
                categoria.getSlug(),
                categoria.getDescripcion(),
                categoria.getImagen() == null ? null : ArchivoRespuesta.de(categoria.getImagen()),
                categoria.getOrden(),
                categoria.isActiva(),
                subcategorias);
    }
}
