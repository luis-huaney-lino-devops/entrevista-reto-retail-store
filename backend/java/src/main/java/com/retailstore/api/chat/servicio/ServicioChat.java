package com.retailstore.api.chat.servicio;

import com.retailstore.api.chat.dominio.Adjunto;
import com.retailstore.api.chat.dominio.AutorMensaje;
import com.retailstore.api.chat.dominio.Conversacion;
import com.retailstore.api.chat.dominio.EstadoConversacion;
import com.retailstore.api.chat.dominio.Mensaje;
import com.retailstore.api.chat.dto.AbrirConversacionPeticion;
import com.retailstore.api.chat.dto.ConversacionDetalleRespuesta;
import com.retailstore.api.chat.dto.ConversacionMiaRespuesta;
import com.retailstore.api.chat.dto.ConversacionResumenRespuesta;
import com.retailstore.api.chat.dto.MensajeRespuesta;
import com.retailstore.api.chat.repositorio.ConversacionRepositorio;
import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.servicio.ServicioCliente;
import com.retailstore.api.comun.auditoria.UsuarioActual;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.comun.tiemporeal.DifusorTiempoReal;
import com.retailstore.api.notificacion.dominio.TipoNotificacion;
import com.retailstore.api.notificacion.servicio.ServicioNotificacion;
import com.retailstore.api.orden.dominio.Orden;
import com.retailstore.api.orden.repositorio.OrdenRepositorio;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atención al cliente.
 *
 * <p>Un mensaje hace siempre tres cosas: se guarda, se empuja a quien esté
 * mirando el hilo y —si lo escribió el cliente— genera una notificación. Van
 * juntas aquí y no repartidas entre el controlador y el WebSocket para que dé
 * igual por dónde entre el mensaje: el resultado es el mismo.
 */
@Service
@Transactional
public class ServicioChat {

    private final ConversacionRepositorio conversaciones;
    private final OrdenRepositorio ordenes;
    private final ServicioCliente clientes;
    private final ServicioAdjunto adjuntos;
    private final ServicioNotificacion notificaciones;
    private final DifusorTiempoReal difusor;
    private final Clock reloj;

    public ServicioChat(ConversacionRepositorio conversaciones, OrdenRepositorio ordenes,
                        ServicioCliente clientes, ServicioAdjunto adjuntos,
                        ServicioNotificacion notificaciones, DifusorTiempoReal difusor, Clock reloj) {
        this.conversaciones = conversaciones;
        this.ordenes = ordenes;
        this.clientes = clientes;
        this.adjuntos = adjuntos;
        this.notificaciones = notificaciones;
        this.difusor = difusor;
        this.reloj = reloj;
    }

    // ============================================================ panel

    @Transactional(readOnly = true)
    public RespuestaPagina<ConversacionResumenRespuesta> listar(String estado, int pagina, int tamanoPagina) {
        var paginacion = PageRequest.of(pagina - 1, tamanoPagina);
        var resultado = (estado == null || estado.isBlank())
                ? conversaciones.findAllByOrderByUltimoMensajeEnDesc(paginacion)
                : conversaciones.findByEstadoOrderByUltimoMensajeEnDesc(estadoDe(estado), paginacion);
        return RespuestaPagina.de(resultado, ConversacionResumenRespuesta::de);
    }

    /**
     * Abre el hilo en el panel. <strong>Marca leído al abrirlo</strong>: quien
     * lo tiene delante ya lo leyó, y obligar a pulsar «marcar como leído» es
     * pedir un clic para decir algo que ya es evidente.
     */
    public ConversacionDetalleRespuesta abrirEnPanel(Long id) {
        Conversacion conversacion = buscar(id);
        if (conversacion.marcarLeidoPorAdmin(reloj.instant()) > 0) {
            notificaciones.resolver("conversacion:" + conversacion.getId());
            difusor.aPanel(Map.of("tipo", "CONVERSACION_LEIDA", "conversacionId", conversacion.getId()));
        }
        conversaciones.flush();
        return ConversacionDetalleRespuesta.de(conversacion, true);
    }

    public ConversacionDetalleRespuesta abrir(AbrirConversacionPeticion peticion) {
        Cliente cliente = clientes.referencia(peticion.clienteId());
        Orden orden = peticion.ordenId() == null ? null
                : ordenes.findById(peticion.ordenId())
                        .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.ORDER_NOT_FOUND,
                                "No existe la orden " + peticion.ordenId() + "."));

        Conversacion conversacion = conversaciones.save(
                new Conversacion(cliente, orden, peticion.asunto().trim()));

        if (peticion.mensaje() != null && !peticion.mensaje().isBlank()) {
            registrar(conversacion, AutorMensaje.ADMINISTRADOR, UsuarioActual.nombre(),
                    peticion.mensaje().trim(), List.of());
        }
        conversaciones.flush();
        return ConversacionDetalleRespuesta.de(conversacion, true);
    }

    public MensajeRespuesta responder(Long id, String cuerpo, List<Long> adjuntoIds) {
        Conversacion conversacion = buscar(id);
        return registrar(conversacion, AutorMensaje.ADMINISTRADOR, UsuarioActual.nombre(), cuerpo, adjuntoIds);
    }

    public ConversacionDetalleRespuesta cambiarEstado(Long id, boolean abierta) {
        Conversacion conversacion = buscar(id);
        if (abierta) {
            conversacion.reabrir();
        } else {
            conversacion.cerrar();
        }
        conversaciones.flush();
        return ConversacionDetalleRespuesta.de(conversacion, true);
    }

    // =========================================================== cliente

    /**
     * El hilo visto por el cliente.
     *
     * <p>Se identifica con el token del hilo, no con una sesión: la tienda
     * puede atender a alguien que compró sin cuenta. Es el mismo mecanismo del
     * carrito.
     */
    @Transactional(readOnly = true)
    public ConversacionDetalleRespuesta porToken(String token) {
        return ConversacionDetalleRespuesta.de(buscarPorToken(token), false);
    }

    /**
     * El hilo visto por el cliente <strong>que ha iniciado sesión</strong>, y
     * marcado como leído al abrirlo.
     *
     * <p>Comprueba que la conversación sea suya. Sin esa comprobación, el token
     * seguiría siendo la única defensa: bastaría uno filtrado para leer el hilo
     * de otra persona desde una cuenta cualquiera.
     */
    public ConversacionDetalleRespuesta abrirComoCliente(String token, Long idCliente) {
        Conversacion conversacion = buscarPorToken(token);
        exigirPropietario(conversacion, idCliente);
        conversacion.marcarLeidoPorCliente(reloj.instant());
        conversaciones.flush();
        return ConversacionDetalleRespuesta.de(conversacion, false);
    }

    /** Las conversaciones del cliente, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<ConversacionMiaRespuesta> mias(Long idCliente) {
        return conversaciones.findByClienteIdOrderByUltimoMensajeEnDesc(idCliente).stream()
                .map(ConversacionMiaRespuesta::de)
                .toList();
    }

    /** Cuántas respuestas sin leer tiene el cliente. Es lo que pinta la campana. */
    @Transactional(readOnly = true)
    public long noLeidosDelCliente(Long idCliente) {
        return conversaciones.noLeidosDelCliente(idCliente);
    }

    /**
     * El cliente abre una consulta, opcionalmente sobre una orden suya.
     *
     * <p><strong>Si ya hay una conversación abierta sobre esa orden, se
     * devuelve esa.</strong> Dos hilos sobre la misma compra terminan con el
     * cliente contando su problema dos veces y con la administración
     * respondiendo en el que nadie mira.
     */
    public ConversacionDetalleRespuesta abrirDesdeLaTienda(Long idCliente, Long idOrden,
                                                           String asunto, String mensaje) {
        Orden orden = null;
        if (idOrden != null) {
            orden = ordenes.findById(idOrden)
                    .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.ORDER_NOT_FOUND,
                            "No existe la orden " + idOrden + "."));
            // La orden tiene que ser suya. Si no, cualquiera podría abrir un
            // hilo sobre la compra de otra persona y ver sus datos en el asunto.
            if (orden.getCliente() == null || !orden.getCliente().getId().equals(idCliente)) {
                throw new ExcepcionAplicacion(CodigoError.ORDER_NOT_FOUND,
                        "No existe la orden " + idOrden + ".");
            }
            var existente = conversaciones.findFirstByOrdenIdOrderByIdDesc(idOrden);
            if (existente.isPresent()) {
                return abrirComoCliente(existente.get().getTokenAcceso().toString(), idCliente);
            }
        }

        Cliente cliente = clientes.referencia(idCliente);
        Conversacion conversacion = conversaciones.save(
                new Conversacion(cliente, orden, asunto.trim()));

        if (mensaje != null && !mensaje.isBlank()) {
            registrar(conversacion, AutorMensaje.CLIENTE, cliente.getNombre(), mensaje.trim(), List.of());
        }
        conversaciones.flush();
        return ConversacionDetalleRespuesta.de(conversacion, false);
    }

    /** Escribir desde la tienda con sesión: se comprueba que el hilo sea suyo. */
    public MensajeRespuesta escribirComoClienteAutenticado(String token, Long idCliente,
                                                           String cuerpo, List<Long> adjuntoIds) {
        Conversacion conversacion = buscarPorToken(token);
        exigirPropietario(conversacion, idCliente);
        return registrar(conversacion, AutorMensaje.CLIENTE,
                conversacion.getCliente().getNombre(), cuerpo, adjuntoIds);
    }

    private void exigirPropietario(Conversacion conversacion, Long idCliente) {
        if (conversacion.getCliente() == null || !conversacion.getCliente().getId().equals(idCliente)) {
            // Mismo código que si no existiera: quien prueba tokens no debe
            // poder distinguir «no existe» de «existe y es de otro».
            throw new ExcepcionAplicacion(CodigoError.CONVERSATION_NOT_FOUND,
                    "No existe esa conversación.");
        }
    }

    public MensajeRespuesta escribirComoCliente(String token, String cuerpo, List<Long> adjuntoIds) {
        Conversacion conversacion = buscarPorToken(token);
        return registrar(conversacion, AutorMensaje.CLIENTE,
                conversacion.getCliente().getNombre(), cuerpo, adjuntoIds);
    }

    // ============================================================ apoyo

    /** El camino único de todo mensaje, venga del panel, de la tienda o del WebSocket. */
    private MensajeRespuesta registrar(Conversacion conversacion, AutorMensaje autor, String autorNombre,
                                       String cuerpo, List<Long> adjuntoIds) {
        String texto = cuerpo == null ? null : cuerpo.trim();
        List<Adjunto> archivos = adjuntos.pendientes(adjuntoIds);

        if ((texto == null || texto.isEmpty()) && archivos.isEmpty()) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR,
                    "El mensaje necesita texto o algún archivo adjunto.");
        }

        Instant ahora = reloj.instant();
        Mensaje mensaje = conversacion.agregarMensaje(autor, autorNombre, texto, ahora);
        archivos.forEach(mensaje::adjuntar);
        conversaciones.flush();

        MensajeRespuesta respuesta = MensajeRespuesta.de(mensaje);

        // A quien esté mirando el hilo, sea el panel o el cliente.
        difusor.aConversacion(conversacion.getId(), Map.of(
                "tipo", "MENSAJE",
                "conversacionId", conversacion.getId(),
                "mensaje", respuesta));

        if (autor == AutorMensaje.CLIENTE) {
            difusor.aPanel(Map.of(
                    "tipo", "CONVERSACION_ACTUALIZADA",
                    "conversacion", ConversacionResumenRespuesta.de(conversacion)));
            notificaciones.publicar(
                    TipoNotificacion.MENSAJE_CLIENTE,
                    "Mensaje de " + conversacion.getCliente().getNombre(),
                    conversacion.getAsunto(),
                    "/conversaciones?abrir=" + conversacion.getId(),
                    "conversacion:" + conversacion.getId());
        }

        return respuesta;
    }

    public Conversacion buscar(Long id) {
        return conversaciones.findConMensajesById(id)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.CONVERSATION_NOT_FOUND,
                        "No existe la conversación " + id + "."));
    }

    private Conversacion buscarPorToken(String token) {
        UUID identificador;
        try {
            identificador = UUID.fromString(token);
        } catch (IllegalArgumentException ex) {
            // Un token mal formado y uno inexistente dan la misma respuesta:
            // distinguirlos convertiría el endpoint en un comprobador de qué
            // conversaciones existen.
            throw conversacionNoEncontrada();
        }
        return conversaciones.findByTokenAcceso(identificador).orElseThrow(ServicioChat::conversacionNoEncontrada);
    }

    private static ExcepcionAplicacion conversacionNoEncontrada() {
        return new ExcepcionAplicacion(CodigoError.CONVERSATION_NOT_FOUND, "Esta conversación no existe.");
    }

    private static EstadoConversacion estadoDe(String valor) {
        try {
            return EstadoConversacion.valueOf(valor.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR,
                    "'" + valor + "' no es un estado de conversación válido.");
        }
    }
}
