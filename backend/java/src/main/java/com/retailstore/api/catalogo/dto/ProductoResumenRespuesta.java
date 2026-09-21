package com.retailstore.api.catalogo.dto;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.archivo.dominio.TamanoVariante;
import com.retailstore.api.catalogo.dominio.Producto;
import java.math.BigDecimal;

/**
 * Tarjeta de producto en la tienda.
 *
 * <p><strong>Sin SKU.</strong> El SKU es un código interno: al comprador no le
 * dice nada y expone la estructura del inventario. El panel usa
 * {@link ProductoAdminRespuesta}, que sí lo lleva.
 *
 * @param imagen URL del tamaño TARJETA, que es el que pinta la rejilla. Enviar
 *               la imagen de detalle aquí multiplica por seis el peso de la
 *               página de listado
 */
public record ProductoResumenRespuesta(
        Long id,
        String nombre,
        String slug,
        String descripcionCorta,
        BigDecimal precio,
        BigDecimal precioAnterior,
        Integer porcentajeDescuento,
        boolean hayStock,
        boolean destacado,
        BigDecimal calificacionPromedio,
        int calificacionConteo,
        String imagen,
        String textoAltImagen,
        ReferenciaRespuesta marca,
        ReferenciaRespuesta subcategoria) {

    public static ProductoResumenRespuesta de(Producto producto) {
        Archivo principal = producto.imagenPrincipal().orElse(null);
        var marca = producto.getMarca();
        var subcategoria = producto.getSubcategoria();
        return new ProductoResumenRespuesta(
                producto.getId(),
                producto.getNombre(),
                producto.getSlug(),
                producto.getDescripcionCorta(),
                producto.getPrecio(),
                producto.getPrecioAnterior(),
                producto.porcentajeDescuento(),
                producto.tieneStock(),
                producto.isDestacado(),
                producto.getCalificacionPromedio(),
                producto.getCalificacionConteo(),
                principal == null ? null : principal.urlDe(TamanoVariante.TARJETA),
                principal == null ? null : principal.getTextoAlt(),
                marca == null ? null : new ReferenciaRespuesta(marca.getId(), marca.getNombre(), marca.getSlug()),
                new ReferenciaRespuesta(subcategoria.getId(), subcategoria.getNombre(), subcategoria.getSlug()));
    }
}
