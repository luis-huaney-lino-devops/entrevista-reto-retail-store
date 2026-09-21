package com.retailstore.api.cliente.dto;

import com.retailstore.api.orden.dto.OrdenResumenRespuesta;
import java.util.List;

/** El cliente con su historial: lo que hace falta antes de escribirle. */
public record ClienteDetalleRespuesta(
        ClienteRespuesta cliente,
        List<OrdenResumenRespuesta> ordenes,
        List<ConversacionDelCliente> conversaciones) {

    public record ConversacionDelCliente(Long id, String asunto, String estado, long noLeidos) {
    }
}
