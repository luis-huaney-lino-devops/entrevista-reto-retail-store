package com.retailstore.api.carrito.dto;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.archivo.dominio.TamanoVariante;
import com.retailstore.api.carrito.dominio.ItemCarrito;
import java.math.BigDecimal;

public record ItemCarritoRespuesta(
        Long id,
        Long productoId,
        String nombre,
        String slug,
        String imagen,
        BigDecimal precioUnitario,
        int cantidad,
        int stockDisponible,
        BigDecimal totalLinea) {

    public static ItemCarritoRespuesta de(ItemCarrito item) {
        var producto = item.getProducto();
        Archivo principal = producto.imagenPrincipal().orElse(null);
        return new ItemCarritoRespuesta(
                item.getId(),
                producto.getId(),
                producto.getNombre(),
                producto.getSlug(),
                principal == null ? null : principal.urlDe(TamanoVariante.MINIATURA),
                producto.getPrecio(),
                item.getCantidad(),
                producto.getStock(),
                item.totalLinea());
    }
}
