package com.retailstore.api.catalogo.dto;

import com.retailstore.api.catalogo.dominio.ImagenProducto;
import com.retailstore.api.catalogo.dominio.Producto;
import java.math.BigDecimal;
import java.util.List;

/** Ficha de producto en la tienda. */
public record ProductoDetalleRespuesta(
        Long id,
        String nombre,
        String slug,
        String descripcionCorta,
        String descripcion,
        BigDecimal precio,
        BigDecimal precioAnterior,
        Integer porcentajeDescuento,
        int stock,
        boolean hayStock,
        boolean destacado,
        BigDecimal calificacionPromedio,
        int calificacionConteo,
        List<ImagenRespuesta> imagenes,
        ReferenciaRespuesta marca,
        ReferenciaRespuesta subcategoria,
        ReferenciaRespuesta categoria) {

    public static ProductoDetalleRespuesta de(Producto producto) {
        var subcategoria = producto.getSubcategoria();
        var categoria = subcategoria.getCategoria();
        var marca = producto.getMarca();
        return new ProductoDetalleRespuesta(
                producto.getId(),
                producto.getNombre(),
                producto.getSlug(),
                producto.getDescripcionCorta(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getPrecioAnterior(),
                producto.porcentajeDescuento(),
                producto.getStock(),
                producto.tieneStock(),
                producto.isDestacado(),
                producto.getCalificacionPromedio(),
                producto.getCalificacionConteo(),
                producto.getImagenes().stream().map(ImagenProducto::getArchivo).map(ImagenRespuesta::de).toList(),
                marca == null ? null : new ReferenciaRespuesta(marca.getId(), marca.getNombre(), marca.getSlug()),
                new ReferenciaRespuesta(subcategoria.getId(), subcategoria.getNombre(), subcategoria.getSlug()),
                new ReferenciaRespuesta(categoria.getId(), categoria.getNombre(), categoria.getSlug()));
    }
}
