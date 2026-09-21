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
