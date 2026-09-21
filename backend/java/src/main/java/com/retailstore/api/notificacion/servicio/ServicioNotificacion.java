package com.retailstore.api.notificacion.servicio;

import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.comun.tiemporeal.DifusorTiempoReal;
import com.retailstore.api.notificacion.dominio.Notificacion;
import com.retailstore.api.notificacion.dominio.TipoNotificacion;
import com.retailstore.api.notificacion.dto.NotificacionRespuesta;
import com.retailstore.api.notificacion.repositorio.NotificacionRepositorio;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las notificaciones del panel.
 *
 * <p>Quien produce una notificación —el catálogo, el chat, las órdenes— no
 * sabe cómo llega ni a quién: llama a {@link #publicar} y sigue con lo suyo.
 */
@Service
@Transactional
public class ServicioNotificacion {

    private static final Logger log = LoggerFactory.getLogger(ServicioNotificacion.class);

    private final NotificacionRepositorio notificaciones;
    private final DifusorTiempoReal difusor;
    private final java.time.Clock reloj;

    public ServicioNotificacion(NotificacionRepositorio notificaciones, DifusorTiempoReal difusor,
                                java.time.Clock reloj) {
        this.notificaciones = notificaciones;
        this.difusor = difusor;
        this.reloj = reloj;
    }

    /**
     * Crea la notificación y la empuja al panel.
     *
     * <p>Va en su propia transacción: una notificación es un efecto
     * <strong>secundario</strong>, y si falla no puede tumbar la operación que
     * la provocó. Que no se avise de un stock agotado es molesto; que no se
     * guarde el producto porque el aviso falló es mucho peor.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publicar(TipoNotificacion tipo, String titulo, String detalle, String enlace,
                         String claveUnicidad) {
        try {
            // Aquí saveAndFlush sí: la entidad es nueva, así que save() hace
            // persist y no merge. En lo que ya está gestionado se usa flush()
            // a secas, porque merge persiste una copia de los hijos nuevos.
            Notificacion notificacion = notificaciones.saveAndFlush(new Notificacion(
                    tipo, titulo, detalle, enlace, claveUnicidad, reloj.instant()));
            difusor.aPanel(Map.of(
                    "tipo", "NOTIFICACION",
                    "notificacion", NotificacionRespuesta.de(notificacion),
                    "pendientes", notificaciones.countByLeidaEnIsNull()));
        } catch (DataIntegrityViolationException ex) {
            // El índice único parcial rechazó una alerta que ya estaba
            // pendiente. No es un error: es exactamente lo que evita que la
            // misma cosa avise diez veces.
            log.debug("Notificación ya pendiente, no se duplica: {}", claveUnicidad);
        }
    }

    /** El hecho que motivó la alerta se resolvió: se cierra sin que nadie la lea. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolver(String claveUnicidad) {
        if (notificaciones.cerrarPorClave(claveUnicidad, reloj.instant()) > 0) {
            difusor.aPanel(Map.of(
                    "tipo", "NOTIFICACIONES_ACTUALIZADAS",
                    "pendientes", notificaciones.countByLeidaEnIsNull()));
        }
    }

    @Transactional(readOnly = true)
    public Bandeja bandeja() {
        return new Bandeja(
                notificaciones.findTop30ByLeidaEnIsNullOrderByCreadoEnDesc().stream()
                        .map(NotificacionRespuesta::de)
                        .toList(),
                notificaciones.countByLeidaEnIsNull());
    }

    @Transactional(readOnly = true)
    public RespuestaPagina<NotificacionRespuesta> historial(int pagina, int tamanoPagina) {
        return RespuestaPagina.de(
                notificaciones.findAllByOrderByCreadoEnDesc(PageRequest.of(pagina - 1, tamanoPagina)),
                NotificacionRespuesta::de);
    }

    public long marcarLeida(Long id) {
        notificaciones.findById(id).ifPresent(notificacion -> notificacion.marcarLeida(reloj.instant()));
        notificaciones.flush();
        return notificaciones.countByLeidaEnIsNull();
    }

    public long marcarTodasLeidas() {
        notificaciones.marcarTodasLeidas(reloj.instant());
        return 0;
    }

    /** Lo que ve la campana del panel. */
    public record Bandeja(List<NotificacionRespuesta> items, long pendientes) {
    }
}
