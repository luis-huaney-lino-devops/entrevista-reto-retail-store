package com.retailstore.api.opinion.repositorio;

import com.retailstore.api.opinion.dominio.Opinion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpinionRepositorio extends JpaRepository<Opinion, Long> {

    /**
     * Las opiniones vivas de un producto. El {@code @SQLRestriction} de la
     * entidad deja fuera las eliminadas sin que haya que acordarse aquí.
     *
     * <p>El grafo trae el cliente porque la respuesta lleva el nombre del
     * autor: sin él, pintar diez opiniones son once consultas.
     */
    @EntityGraph(attributePaths = {"cliente"})
    Page<Opinion> findByProductoId(Long idProducto, Pageable paginacion);

    /** La opinión de un cliente sobre un producto, si la escribió y no la retiró. */
    @EntityGraph(attributePaths = {"cliente", "producto"})
    Optional<Opinion> findByProductoIdAndClienteId(Long idProducto, Long idCliente);

    /**
     * Acotada por cliente a propósito: así no hay forma de escribir por
     * descuido un servicio que edite la opinión de otra persona. La de otro no
     * es un 403, es un 404 (RN-094).
     */
    @EntityGraph(attributePaths = {"cliente", "producto"})
    Optional<Opinion> findByIdAndClienteId(Long id, Long idCliente);

    /**
     * Cuántas opiniones hay de cada número de estrellas, en una consulta.
     *
     * <p>Solo devuelve las notas que existen: las que faltan las completa el
     * servicio con cero, porque la tienda necesita las cinco barras siempre.
     */
    @Query("""
            select o.calificacion, count(o)
              from Opinion o
             where o.producto.id = :idProducto
             group by o.calificacion""")
    List<Object[]> desglosePorProducto(@Param("idProducto") Long idProducto);

    /**
     * Vuelve a derivar el promedio y el conteo del producto desde esta tabla
     * (RN-093). Es la única forma de escribir esas dos columnas.
     *
     * <p><strong>SQL nativo y una sola sentencia.</strong> Nativo porque JPQL no
     * tiene {@code update ... from} ni subconsultas en el {@code set}; una sola
     * porque así el valor que queda es el que había en la tabla en ese
     * instante, sin una ventana entre leer y escribir.
     *
     * <p><strong>No toca {@code version} ni la auditoría del producto.</strong>
     * Una opinión no es una edición del producto: contarla como tal le daría un
     * {@code CONCURRENT_MODIFICATION} al administrador que lo tuviera abierto y
     * le diría que alguien lo modificó cuando nadie lo hizo. Es el mismo
     * razonamiento del contador de vistas.
     *
     * <p>El {@code coalesce} es el caso de la última opinión eliminada: sin
     * filas, {@code avg} devuelve nulo y la columna es NOT NULL.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update producto p
               set calificacion_promedio = coalesce((
                       select round(avg(o.calificacion)::numeric, 1)
                         from opinion o
                        where o.fk_id_producto = p.id_producto
                          and o.eliminado_en is null), 0),
                   calificacion_conteo = (
                       select count(*)
                         from opinion o
                        where o.fk_id_producto = p.id_producto
                          and o.eliminado_en is null)
             where p.id_producto = :idProducto""", nativeQuery = true)
    void recalcularCalificacionDe(@Param("idProducto") Long idProducto);
}
