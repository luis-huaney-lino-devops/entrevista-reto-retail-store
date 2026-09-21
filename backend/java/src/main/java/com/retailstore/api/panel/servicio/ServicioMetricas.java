package com.retailstore.api.panel.servicio;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.archivo.dominio.TamanoVariante;
import com.retailstore.api.catalogo.repositorio.ProductoRepositorio;
import com.retailstore.api.orden.dominio.EstadoOrden;
import com.retailstore.api.orden.repositorio.OrdenRepositorio;
import com.retailstore.api.panel.dto.MetricasRespuesta;
import com.retailstore.api.panel.dto.MetricasRespuesta.ConteoEstado;
import com.retailstore.api.panel.dto.MetricasRespuesta.ProductoVendido;
import com.retailstore.api.panel.dto.MetricasRespuesta.ProductoVisto;
import com.retailstore.api.panel.dto.MetricasRespuesta.PuntoVenta;
import com.retailstore.api.panel.dto.MetricasRespuesta.Resumen;
import com.retailstore.api.panel.dto.MetricasRespuesta.StockBajo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las cifras del tablero.
 *
 * <p>Todas las agregaciones se hacen en la base. Traer noventa días de órdenes
 * a memoria para sumarlas en Java funciona con datos de demostración y deja de
 * funcionar exactamente cuando la tienda empieza a vender.
 */
@Service
@Transactional(readOnly = true)
public class ServicioMetricas {

    private static final int DIAS_SERIE = 30;
    private static final int STOCK_BAJO = 10;
    private static final int TOPE_LISTAS = 8;

    private final OrdenRepositorio ordenes;
    private final ProductoRepositorio productos;
    private final Clock reloj;

    public ServicioMetricas(OrdenRepositorio ordenes, ProductoRepositorio productos, Clock reloj) {
        this.ordenes = ordenes;
        this.productos = productos;
        this.reloj = reloj;
    }

    public MetricasRespuesta calcular() {
        Instant ahora = reloj.instant();
        Instant desde = ahora.minus(Duration.ofDays(DIAS_SERIE));

        List<PuntoVenta> serie = serieDeVentas(desde);
        List<ConteoEstado> porEstado = porEstado();

        long ordenes30 = serie.stream().mapToLong(PuntoVenta::ordenes).sum();
        BigDecimal ventas30 = serie.stream()
                .map(PuntoVenta::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MetricasRespuesta(
                new Resumen(
                        ventas30,
                        ordenes30,
                        // Sin órdenes no hay promedio. Devolver cero sugeriría
                        // que el ticket medio es cero, que es distinto de
                        // «todavía no hay datos».
                        ordenes30 == 0 ? null : ventas30.divide(BigDecimal.valueOf(ordenes30), 2, RoundingMode.HALF_UP),
                        ordenes.countByEstado(EstadoOrden.PENDIENTE),
                        productos.countByActivoTrue(),
                        productos.countByActivoFalse(),
                        productos.countByActivoTrueAndStock(0),
                        productos.totalVistas(),
                        productos.valorInventario()),
                serie,
                porEstado,
                masVistos(),
                masVendidos(),
                stockBajo());
    }

    /**
     * Serie diaria <strong>densa</strong>: los días sin ventas van con cero.
     *
     * <p>La base solo devuelve los días que tuvieron alguna orden. Si se
     * pintara eso tal cual, el gráfico uniría el día 3 con el día 7 en línea
     * recta y daría a entender que hubo ventas los días 4, 5 y 6.
     */
    private List<PuntoVenta> serieDeVentas(Instant desde) {
        Map<LocalDate, PuntoVenta> porFecha = new HashMap<>();
        for (Object[] fila : ordenes.ventasPorDiaDesde(desde)) {
            LocalDate fecha = aFecha(fila[0]);
            porFecha.put(fecha, new PuntoVenta(
                    fecha,
                    (BigDecimal) fila[1],
                    ((Number) fila[2]).longValue()));
        }

        LocalDate hoy = LocalDate.ofInstant(reloj.instant(), ZoneOffset.UTC);
        List<PuntoVenta> serie = new ArrayList<>(DIAS_SERIE + 1);
        for (int atras = DIAS_SERIE; atras >= 0; atras--) {
            LocalDate fecha = hoy.minusDays(atras);
            serie.add(porFecha.getOrDefault(fecha, new PuntoVenta(fecha, BigDecimal.ZERO, 0)));
        }
        return serie;
    }

    /** Todos los estados, también los que no tienen ninguna orden. */
    private List<ConteoEstado> porEstado() {
        Map<EstadoOrden, Object[]> filas = new HashMap<>();
        for (Object[] fila : ordenes.resumenPorEstado()) {
            filas.put((EstadoOrden) fila[0], fila);
        }
        List<ConteoEstado> resultado = new ArrayList<>();
        for (EstadoOrden estado : EstadoOrden.values()) {
            Object[] fila = filas.get(estado);
            resultado.add(new ConteoEstado(
                    estado.name(),
                    estado.etiqueta(),
                    fila == null ? 0 : ((Number) fila[1]).longValue(),
                    fila == null ? BigDecimal.ZERO : (BigDecimal) fila[2]));
        }
        return resultado;
    }

    private List<ProductoVisto> masVistos() {
        return productos.findTop8ByActivoTrueOrderByVistasDesc().stream()
                .map(producto -> new ProductoVisto(
                        producto.getId(),
                        producto.getNombre(),
                        producto.getSlug(),
                        producto.getVistas(),
                        producto.imagenPrincipal().map(archivo -> archivo.urlDe(TamanoVariante.MINIATURA)).orElse(null)))
                .toList();
    }

    private List<ProductoVendido> masVendidos() {
        return ordenes.masVendidos(PageRequest.of(0, TOPE_LISTAS)).stream()
                .map(fila -> new ProductoVendido(
                        (String) fila[0],
                        ((Number) fila[1]).longValue(),
                        (BigDecimal) fila[2]))
                .toList();
    }

    private List<StockBajo> stockBajo() {
        return productos.findTop8ByActivoTrueAndStockLessThanOrderByStockAsc(STOCK_BAJO).stream()
                .map(producto -> new StockBajo(
                        producto.getId(), producto.getNombre(), producto.getSku(), producto.getStock()))
                .toList();
    }

    /**
     * El {@code cast(... as date)} de JPQL llega como {@link java.sql.Date} o
     * como {@link LocalDate} según el driver. Se acepta cualquiera de los dos
     * en lugar de confiar en uno.
     */
    private static LocalDate aFecha(Object valor) {
        if (valor instanceof LocalDate fecha) {
            return fecha;
        }
        if (valor instanceof java.sql.Date fecha) {
            return fecha.toLocalDate();
        }
        if (valor instanceof java.util.Date fecha) {
            return fecha.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        throw new IllegalStateException("Tipo de fecha inesperado: " + valor.getClass());
    }
}
