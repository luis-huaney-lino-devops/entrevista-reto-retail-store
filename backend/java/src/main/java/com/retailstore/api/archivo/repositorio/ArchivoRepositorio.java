package com.retailstore.api.archivo.repositorio;

import com.retailstore.api.archivo.dominio.Archivo;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchivoRepositorio extends JpaRepository<Archivo, Long> {

    /** Deduplicación: la misma imagen subida dos veces es un solo archivo. */
    @EntityGraph(attributePaths = "variantes")
    Optional<Archivo> findByHashContenido(String hashContenido);

    /**
     * El {@code EntityGraph} trae las variantes en la misma consulta. Sin él,
     * listar 20 archivos son 21 consultas, y el listado del panel se nota.
     */
    @EntityGraph(attributePaths = "variantes")
    Page<Archivo> findAllByOrderByIdDesc(Pageable paginacion);

    @EntityGraph(attributePaths = "variantes")
    Optional<Archivo> findWithVariantesById(Long id);
}
