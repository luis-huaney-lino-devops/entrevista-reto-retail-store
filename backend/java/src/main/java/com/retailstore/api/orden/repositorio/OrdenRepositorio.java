package com.retailstore.api.orden.repositorio;

import com.retailstore.api.orden.dominio.EstadoOrden;
import com.retailstore.api.orden.dominio.Orden;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenRepositorio extends JpaRepository<Orden, Long> {

    @EntityGraph(attributePaths = {"items", "items.producto"})
    Optional<Orden> findConItemsById(Long id);

    Page<Orden> findAllByOrderByCreadoEnDesc(Pageable paginacion);

    Page<Orden> findByEstadoOrderByCreadoEnDesc(EstadoOrden estado, Pageable paginacion);

    long countByEstado(EstadoOrden estado);

    List<Orden> findByClienteIdOrderByCreadoEnDesc(Long idCliente);

    /**
     * Cuántas órdenes y cuánto gastó cada cliente de la página, en una sola
     * consulta. Una por cliente serían veintiuna para pintar veinte filas.
     */
    @Query("""
            select o.cliente.id, count(o), coalesce(sum(o.total), 0)
              from Orden o
             where o.cliente.id in :ids
               and o.estado <> com.retailstore.api.orden.dominio.EstadoOrden.CANCELADA
             group by o.cliente.id""")
    List<Object[]> resumenPorCliente(@Param("ids") List<Long> ids);

    /**
     * Ventas por día. Se agrega en la base y no en Java: traer 90 días de
     * órdenes para sumarlas en memoria es mover trabajo al sitio equivocado.
     */
    @Query("""
            select cast(o.creadoEn as date) as dia, sum(o.total) as total, count(o) as cuantas
              from Orden o
             where o.creadoEn >= :desde
               and o.estado <> com.retailstore.api.orden.dominio.EstadoOrden.CANCELADA
             group by cast(o.creadoEn as date)
             order by cast(o.creadoEn as date)""")
    List<Object[]> ventasPorDiaDesde(@Param("desde") Instant desde);

    @Query("""
            select o.estado, count(o), coalesce(sum(o.total), 0)
              from Orden o
             group by o.estado""")
    List<Object[]> resumenPorEstado();

    @Query("""
            select coalesce(sum(o.total), 0)
              from Orden o
             where o.creadoEn >= :desde
               and o.estado <> com.retailstore.api.orden.dominio.EstadoOrden.CANCELADA""")
    java.math.BigDecimal ventasDesde(@Param("desde") Instant desde);

    @Query("""
            select i.nombreProducto, sum(i.cantidad), sum(i.totalLinea)
              from ItemOrden i
             where i.orden.estado <> com.retailstore.api.orden.dominio.EstadoOrden.CANCELADA
             group by i.nombreProducto
             order by sum(i.totalLinea) desc""")
    List<Object[]> masVendidos(Pageable limite);
}
