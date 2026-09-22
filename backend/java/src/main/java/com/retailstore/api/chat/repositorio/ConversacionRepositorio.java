package com.retailstore.api.chat.repositorio;

import com.retailstore.api.chat.dominio.Conversacion;
import com.retailstore.api.chat.dominio.EstadoConversacion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversacionRepositorio extends JpaRepository<Conversacion, Long> {

    @EntityGraph(attributePaths = {"cliente", "mensajes", "mensajes.adjuntos"})
    Optional<Conversacion> findConMensajesById(Long id);

    @EntityGraph(attributePaths = {"cliente", "mensajes", "mensajes.adjuntos"})
    Optional<Conversacion> findByTokenAcceso(UUID tokenAcceso);

    @EntityGraph(attributePaths = "cliente")
    Page<Conversacion> findAllByOrderByUltimoMensajeEnDesc(Pageable paginacion);

    @EntityGraph(attributePaths = "cliente")
    Page<Conversacion> findByEstadoOrderByUltimoMensajeEnDesc(EstadoConversacion estado, Pageable paginacion);

    /**
     * La conversación más reciente sobre una orden.
     *
     * <p>Podría haber varias si se abrieron y cerraron distintas incidencias;
     * la que interesa al abrir la orden es la última.
     */
    Optional<Conversacion> findFirstByOrdenIdOrderByIdDesc(Long idOrden);

    /** La bandeja del cliente en la tienda, ordenada por actividad. */
    java.util.List<Conversacion> findByClienteIdOrderByUltimoMensajeEnDesc(Long idCliente);

    /**
     * Lo que pinta la campana de la tienda. Se suma en la base y no en memoria:
     * traer todas las conversaciones para sumar un entero sería cargar los
     * hilos enteros en cada visita.
     */
    @Query("select coalesce(sum(c.noLeidosCliente), 0) from Conversacion c where c.cliente.id = :idCliente")
    long noLeidosDelCliente(@Param("idCliente") Long idCliente);

    @Query("select coalesce(sum(c.noLeidosAdmin), 0) from Conversacion c")
    long totalNoLeidos();

    @Query("select count(c) from Conversacion c where c.noLeidosAdmin > 0")
    long conversacionesConPendientes();
}
