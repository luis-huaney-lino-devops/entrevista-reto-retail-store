package com.retailstore.api.notificacion.repositorio;

import com.retailstore.api.notificacion.dominio.Notificacion;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionRepositorio extends JpaRepository<Notificacion, Long> {

    Page<Notificacion> findAllByOrderByCreadoEnDesc(Pageable paginacion);

    List<Notificacion> findTop30ByLeidaEnIsNullOrderByCreadoEnDesc();

    long countByLeidaEnIsNull();

    @Modifying
    @Query("update Notificacion n set n.leidaEn = :momento where n.leidaEn is null")
    int marcarTodasLeidas(@Param("momento") Instant momento);

    /**
     * Cierra la alerta pendiente de algo que ya se resolvió.
     *
     * <p>Un producto que vuelve a tener stock no debería dejar su aviso de
     * «agotado» esperando a que alguien lo cierre a mano.
     */
    @Modifying
    @Query("update Notificacion n set n.leidaEn = :momento where n.claveUnicidad = :clave and n.leidaEn is null")
    int cerrarPorClave(@Param("clave") String clave, @Param("momento") Instant momento);
}
