package com.retailstore.api.catalogo.dto;

import com.retailstore.api.catalogo.dominio.ImagenProducto;
import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.opinion.dto.DesgloseCalificacionRespuesta;
import java.math.BigDecimal;
import java.util.List;

/**
 * Ficha de producto en la tienda.
 *
 * <p>Lleva el desglose por estrellas además del promedio (RN-093). Es lo
 * primero que mira quien va a leer opiniones -un 4,3 con cincuenta cincos y
 * diez unos no es el mismo producto que un 4,3 con todo cuatros- y pedirlo
 * aparte sería una segunda petición para pintar lo que va justo debajo del
 * nombre.
 *
 * <p>El desglose no sale del producto sino de la tabla de opiniones, así que
 * llega como parámetro: la ficha no se puede armar «olvidándolo» y devolviendo
 * un producto sin él.
 */
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
        List<DesgloseCalificacionRespuesta> desgloseCalificacion,
        List<ImagenRespuesta> imagenes,
        ReferenciaRespuesta marca,
        ReferenciaRespuesta subcategoria,
        ReferenciaRespuesta categoria) {

    public static ProductoDetalleRespuesta de(Producto producto,
                                              List<DesgloseCalificacionRespuesta> desglose) {
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
                desglose,
                producto.getImagenes().stream().map(ImagenProducto::getArchivo).map(ImagenRespuesta::de).toList(),
                marca == null ? null : new ReferenciaRespuesta(marca.getId(), marca.getNombre(), marca.getSlug()),
                new ReferenciaRespuesta(subcategoria.getId(), subcategoria.getNombre(), subcategoria.getSlug()),
                new ReferenciaRespuesta(categoria.getId(), categoria.getNombre(), categoria.getSlug()));
    }
}
