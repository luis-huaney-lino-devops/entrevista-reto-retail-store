package com.retailstore.api.cliente.repositorio;

import com.retailstore.api.cliente.dominio.Cliente;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepositorio extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByEmail(String email);

    /**
     * El cliente de una sesión de la tienda.
     *
     * <p>Consulta HQL y no {@code findById} a propósito: la
     * {@code @SQLRestriction} de la entidad no se aplica a la búsqueda por
     * clave primaria, así que {@code findById} devolvería también a un cliente
     * eliminado. Aquí sí se aplica, y el {@code activo} cierra el otro caso:
     * un token de 15 minutos emitido justo antes de bloquear la cuenta.
     */
    @Query("select c from Cliente c where c.id = :id and c.activo = true")
    Optional<Cliente> buscarActivo(@Param("id") Long id);

    Page<Cliente> findAllByOrderByNombreAsc(Pageable paginacion);

    /**
     * Busca por nombre o por correo con una sola consulta. El comodín del
     * usuario se escapa igual que en el catálogo (RN-026): un `%` escrito en el
     * buscador es un porcentaje, no «todo».
     */
    @Query("""
            select c from Cliente c
             where lower(c.nombre) like lower(:patron) escape '\\'
                or lower(c.email)  like lower(:patron) escape '\\'
             order by c.nombre""")
    Page<Cliente> buscar(@Param("patron") String patron, Pageable paginacion);

    long countByActivoTrue();

    @Query("select count(c) from Cliente c where c.creadoEn >= :desde")
    long contarNuevosDesde(@Param("desde") java.time.Instant desde);
}
