package com.retailstore.api.orden.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.chat.repositorio.ConversacionRepositorio;
import com.retailstore.api.chat.dominio.Conversacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.orden.dominio.EstadoOrden;
import com.retailstore.api.orden.dominio.ItemOrden;
import com.retailstore.api.orden.dominio.Orden;
import com.retailstore.api.orden.dto.OrdenDetalleRespuesta;
import com.retailstore.api.orden.dto.OrdenResumenRespuesta;
import com.retailstore.api.notificacion.servicio.AvisosDeStock;
import com.retailstore.api.orden.repositorio.OrdenRepositorio;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Órdenes desde el panel: consultarlas y moverlas de estado.
 *
 * <p>Aquí no se crean. La orden nace en el checkout de la tienda, que todavía
 * no existe; cuando exista, escribirá en estas mismas tablas.
 */
@Service
@Transactional
public class ServicioOrden {

    private final OrdenRepositorio ordenes;
    private final ConversacionRepositorio conversaciones;
    private final AvisosDeStock avisos;

    public ServicioOrden(OrdenRepositorio ordenes, ConversacionRepositorio conversaciones,
                         AvisosDeStock avisos) {
        this.ordenes = ordenes;
        this.conversaciones = conversaciones;
        this.avisos = avisos;
    }

    @Transactional(readOnly = true)
    public RespuestaPagina<OrdenResumenRespuesta> listar(String estado, int pagina, int tamanoPagina) {
        var paginacion = PageRequest.of(pagina - 1, tamanoPagina);
        var resultado = (estado == null || estado.isBlank())
                ? ordenes.findAllByOrderByCreadoEnDesc(paginacion)
                : ordenes.findByEstadoOrderByCreadoEnDesc(estadoDe(estado), paginacion);
        return RespuestaPagina.de(resultado, OrdenResumenRespuesta::de);
    }

    @Transactional(readOnly = true)
    public OrdenDetalleRespuesta porId(Long id) {
        // La conversación se busca aquí y no en el mapeador: el DTO no tiene
        // por qué saber de repositorios.
        return OrdenDetalleRespuesta.de(
                buscar(id),
                conversaciones.findFirstByOrdenIdOrderByIdDesc(id).map(Conversacion::getId).orElse(null));
    }

    /**
     * Mueve la orden de estado. Cancelar devuelve el stock (RN-055).
     *
     * <p>El stock se devuelve aquí, en la misma transacción que el cambio de
     * estado: si se hiciera después, una orden podría quedar cancelada sin que
     * las unidades volvieran al inventario.
     */
    public OrdenDetalleRespuesta cambiarEstado(Long id, String estadoSolicitado) {
        Orden orden = buscar(id);
        if (orden.moverA(estadoDe(estadoSolicitado))) {
            for (ItemOrden item : orden.getItems()) {
                item.getProducto().reponerStock(item.getCantidad());
                // Las unidades vuelven al inventario: si había un aviso de
                // agotado, deja de tener sentido y se cierra solo.
                avisos.revisar(item.getProducto());
            }
        }
        // Volcar antes de mapear: la auditoría -quién y cuándo- la escribe el
        // listener al volcar, asi que sin esto la respuesta devolveria el
        // valor anterior.
        ordenes.flush();
        return OrdenDetalleRespuesta.de(orden);
    }

    private Orden buscar(Long id) {
        return ordenes.findConItemsById(id)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.ORDER_NOT_FOUND,
                        "No existe la orden " + id + "."));
    }

    private static EstadoOrden estadoDe(String valor) {
        try {
            return EstadoOrden.valueOf(valor.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR,
                    "'" + valor + "' no es un estado de orden válido.");
        }
    }
}
