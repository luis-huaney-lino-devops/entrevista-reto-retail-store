package com.retailstore.api.orden.dto;

import com.retailstore.api.orden.dominio.ItemOrden;
import com.retailstore.api.orden.dominio.Orden;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrdenDetalleRespuesta(
        Long id,
        String numero,
        String nombreContacto,
        String email,
        String telefono,
        String direccion,
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal total,
        String codigoCupon,
        String estado,
        String estadoEtiqueta,
        /** Quién la hizo. Null en una orden de invitado sin cuenta asociada. */
        Long clienteId,
        String clienteNombre,
        /** La conversación abierta sobre esta orden, si ya hay una. */
        Long conversacionId,
        /** A dónde se puede mover desde aquí. Vacío = estado terminal. */
        List<String> transicionesPermitidas,
        List<Linea> items,
        Instant creadoEn,
        Instant actualizadoEn,
        String actualizadoPor) {

    public record Linea(Long productoId, String nombreProducto, String sku,
                        BigDecimal precioUnitario, int cantidad, BigDecimal totalLinea) {
        static Linea de(ItemOrden item) {
            return new Linea(item.getProducto().getId(), item.getNombreProducto(), item.getSku(),
                    item.getPrecioUnitario(), item.getCantidad(), item.getTotalLinea());
        }
    }

    public static OrdenDetalleRespuesta de(Orden orden) {
        return de(orden, null);
    }

    public static OrdenDetalleRespuesta de(Orden orden, Long conversacionId) {
        var cliente = orden.getCliente();
        return new OrdenDetalleRespuesta(
                orden.getId(),
                orden.getNumero(),
                orden.getNombreContacto(),
                orden.getEmail(),
                orden.getTelefono(),
                orden.getDireccion(),
                orden.getSubtotal(),
                orden.getDescuento(),
                orden.getTotal(),
                orden.getCodigoCupon(),
                orden.getEstado().name(),
                orden.getEstado().etiqueta(),
                cliente == null ? null : cliente.getId(),
                cliente == null ? null : cliente.getNombre(),
                conversacionId,
                orden.getEstado().siguientes().stream().map(Enum::name).sorted().toList(),
                orden.getItems().stream().map(Linea::de).toList(),
                orden.getCreadoEn(),
                orden.getActualizadoEn(),
                orden.getActualizadoPor());
    }
}
