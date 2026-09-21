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

    @Query("select coalesce(sum(c.noLeidosAdmin), 0) from Conversacion c")
    long totalNoLeidos();

    @Query("select count(c) from Conversacion c where c.noLeidosAdmin > 0")
    long conversacionesConPendientes();
}
