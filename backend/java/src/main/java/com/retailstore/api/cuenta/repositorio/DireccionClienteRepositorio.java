package com.retailstore.api.cuenta.repositorio;

import com.retailstore.api.cuenta.dominio.DireccionCliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Todas las consultas van acotadas por cliente.
 *
 * <p>No es comodidad: buscar primero por id y comprobar el dueño después
 * distingue «no existe» de «no es tuya» en el tiempo de respuesta y en el
 * código de error. Acotando desde la consulta, la dirección de otro es un 404
 * como cualquier otra que no existe.
 */
public interface DireccionClienteRepositorio extends JpaRepository<DireccionCliente, Long> {

    /** El grafo evita tres consultas por fila para pintar distrito/provincia/departamento. */
    @EntityGraph(attributePaths = {"distrito", "distrito.provincia", "distrito.provincia.departamento"})
    List<DireccionCliente> findByClienteIdOrderByPredeterminadaDescIdAsc(Long idCliente);

    @EntityGraph(attributePaths = {"distrito", "distrito.provincia", "distrito.provincia.departamento"})
    Optional<DireccionCliente> findByIdAndClienteId(Long id, Long idCliente);

    long countByClienteId(Long idCliente);
}
