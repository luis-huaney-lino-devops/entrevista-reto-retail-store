package com.retailstore.api.ubigeo.repositorio;

import com.retailstore.api.ubigeo.dominio.Distrito;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistritoRepositorio extends JpaRepository<Distrito, Integer> {

    List<Distrito> findByProvinciaIdOrderByNombreAsc(Short idProvincia);

    /**
     * Distrito con su provincia y su departamento en una sola consulta: al
     * guardar una dirección hay que validar el distrito y luego devolver los
     * tres niveles, y sin el grafo eso son tres viajes a la base.
     */
    @EntityGraph(attributePaths = {"provincia", "provincia.departamento"})
    Optional<Distrito> findConJerarquiaById(Integer id);
}
