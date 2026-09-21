package com.retailstore.api.cliente.servicio;

import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.dto.ClienteDetalleRespuesta;
import com.retailstore.api.cliente.dto.ClienteRespuesta;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.orden.dto.OrdenResumenRespuesta;
import com.retailstore.api.orden.repositorio.OrdenRepositorio;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServicioCliente {

    private final ClienteRepositorio clientes;
    private final OrdenRepositorio ordenes;

    public ServicioCliente(ClienteRepositorio clientes, OrdenRepositorio ordenes) {
        this.clientes = clientes;
        this.ordenes = ordenes;
    }

    public RespuestaPagina<ClienteRespuesta> listar(String texto, int pagina, int tamanoPagina) {
        var paginacion = PageRequest.of(pagina - 1, tamanoPagina);
        Page<Cliente> resultado = (texto == null || texto.isBlank())
                ? clientes.findAllByOrderByNombreAsc(paginacion)
                : clientes.buscar("%" + escapar(texto.trim()) + "%", paginacion);

        // Una consulta para todos los totales, no una por cliente: con veinte
        // filas por página, lo segundo son veintiuna consultas.
        Map<Long, long[]> conteos = new HashMap<>();
        Map<Long, BigDecimal> totales = new HashMap<>();
        List<Long> ids = resultado.getContent().stream().map(Cliente::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] fila : ordenes.resumenPorCliente(ids)) {
                Long idCliente = ((Number) fila[0]).longValue();
                conteos.put(idCliente, new long[] {((Number) fila[1]).longValue()});
                totales.put(idCliente, (BigDecimal) fila[2]);
            }
        }

        return RespuestaPagina.de(resultado, cliente -> ClienteRespuesta.de(
                cliente,
                conteos.getOrDefault(cliente.getId(), new long[] {0})[0],
                totales.getOrDefault(cliente.getId(), BigDecimal.ZERO)));
    }

    public ClienteDetalleRespuesta porId(Long id) {
        Cliente cliente = buscar(id);
        List<OrdenResumenRespuesta> suyas = ordenes.findByClienteIdOrderByCreadoEnDesc(id).stream()
                .map(OrdenResumenRespuesta::de)
                .toList();
        BigDecimal total = suyas.stream()
                .map(OrdenResumenRespuesta::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ClienteDetalleRespuesta(
                ClienteRespuesta.de(cliente, suyas.size(), total),
                suyas,
                List.of());
    }

    @Transactional
    public ClienteRespuesta cambiarEstado(Long id, boolean activo) {
        Cliente cliente = buscar(id);
        if (activo) {
            cliente.activar();
        } else {
            cliente.desactivar();
        }
        clientes.flush();
        return ClienteRespuesta.de(cliente, 0, BigDecimal.ZERO);
    }

    /** Para el chat: el cliente que está detrás de una conversación. */
    public Cliente referencia(Long id) {
        return buscar(id);
    }

    private Cliente buscar(Long id) {
        return clientes.findById(id)
                .filter(cliente -> !cliente.estaEliminado())
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.CUSTOMER_NOT_FOUND,
                        "No existe el cliente " + id + "."));
    }

    /** RN-026: los comodines que escribe quien busca son texto, no patrón. */
    private static String escapar(String termino) {
        return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
