package com.retailstore.api.panel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Todo lo que pinta el tablero, en <strong>una sola petición</strong>.
 *
 * <p>Seis peticiones separadas harían seis viajes, seis estados de carga y seis
 * formas de que la pantalla se quede a medias. Aquí el tablero está o no está.
 *
 * @param ventasPorDia serie completa de los últimos 30 días, <strong>incluidos
 *                     los días sin ventas</strong>. Si se omitieran, el gráfico
 *                     uniría el día 3 con el día 7 en línea recta y parecería
 *                     que hubo ventas en medio
 */
public record MetricasRespuesta(
        Resumen resumen,
        List<PuntoVenta> ventasPorDia,
        List<ConteoEstado> ordenesPorEstado,
        List<ProductoVisto> masVistos,
        List<ProductoVendido> masVendidos,
        List<StockBajo> stockBajo) {

    /**
     * @param ticketPromedio total vendido entre número de órdenes. Null si no
     *                       hubo ninguna: un promedio de cero es mentira, y
     *                       dividir entre cero es peor
     */
    public record Resumen(
            BigDecimal ventas30Dias,
            long ordenes30Dias,
            BigDecimal ticketPromedio,
            long ordenesPendientes,
            long productosActivos,
            long productosBorrador,
            long productosSinStock,
            long vistasTotales,
            BigDecimal valorInventario) {
    }

    public record PuntoVenta(LocalDate fecha, BigDecimal total, long ordenes) {
    }

    public record ConteoEstado(String estado, String etiqueta, long cantidad, BigDecimal total) {
    }

    public record ProductoVisto(Long id, String nombre, String slug, long vistas, String imagen) {
    }

    public record ProductoVendido(String nombre, long unidades, BigDecimal total) {
    }

    public record StockBajo(Long id, String nombre, String sku, int stock) {
    }
}
