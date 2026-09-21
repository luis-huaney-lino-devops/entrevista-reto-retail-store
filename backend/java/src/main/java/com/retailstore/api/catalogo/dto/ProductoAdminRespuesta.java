package com.retailstore.api.catalogo.dto;

import com.retailstore.api.catalogo.dominio.ImagenProducto;
import com.retailstore.api.catalogo.dominio.Producto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Producto tal como lo ve el panel: con SKU, con estado y con auditoría.
 *
 * <p>Un producto inactivo es 404 en la tienda y 200 aquí. No es una
 * incoherencia: para el comprador ese producto no existe, y decirle «existe
 * pero está oculto» filtra el catálogo interno.
 */
public record ProductoAdminRespuesta(
        Long id,
        String sku,
        String nombre,
        String slug,
        String descripcionCorta,
        String descripcion,
        BigDecimal precio,
        BigDecimal precioAnterior,
        Integer porcentajeDescuento,
        int stock,
        boolean destacado,
        boolean activo,
        BigDecimal calificacionPromedio,
        int calificacionConteo,
        long vistas,
        List<ImagenRespuesta> imagenes,
        ReferenciaRespuesta marca,
        ReferenciaRespuesta subcategoria,
        ReferenciaRespuesta categoria,
        Instant creadoEn,
        String creadoPor,
        Instant actualizadoEn,
        String actualizadoPor,
        int version) {

    public static ProductoAdminRespuesta de(Producto producto) {
        var subcategoria = producto.getSubcategoria();
        var categoria = subcategoria.getCategoria();
        var marca = producto.getMarca();
        return new ProductoAdminRespuesta(
                producto.getId(),
                producto.getSku(),
                producto.getNombre(),
                producto.getSlug(),
                producto.getDescripcionCorta(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getPrecioAnterior(),
                producto.porcentajeDescuento(),
                producto.getStock(),
                producto.isDestacado(),
                producto.isActivo(),
                producto.getCalificacionPromedio(),
                producto.getCalificacionConteo(),
                producto.getVistas(),
                producto.getImagenes().stream().map(ImagenProducto::getArchivo).map(ImagenRespuesta::de).toList(),
                marca == null ? null : new ReferenciaRespuesta(marca.getId(), marca.getNombre(), marca.getSlug()),
                new ReferenciaRespuesta(subcategoria.getId(), subcategoria.getNombre(), subcategoria.getSlug()),
                new ReferenciaRespuesta(categoria.getId(), categoria.getNombre(), categoria.getSlug()),
                producto.getCreadoEn(),
                producto.getCreadoPor(),
                producto.getActualizadoEn(),
                producto.getActualizadoPor(),
                producto.getVersion());
    }
}
