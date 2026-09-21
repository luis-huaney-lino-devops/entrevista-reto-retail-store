package com.retailstore.api.chat.dto;

import com.retailstore.api.chat.dominio.Conversacion;
import java.util.List;

/**
 * @param tokenAcceso solo se devuelve al panel: es lo que permite probar el
 *                    hilo desde el lado del cliente mientras la tienda no
 *                    existe. Cuando haya sesión de cliente deja de hacer falta
 */
public record ConversacionDetalleRespuesta(
        ConversacionResumenRespuesta conversacion,
        List<MensajeRespuesta> mensajes,
        String tokenAcceso) {

    public static ConversacionDetalleRespuesta de(Conversacion conversacion, boolean incluirToken) {
        return new ConversacionDetalleRespuesta(
                ConversacionResumenRespuesta.de(conversacion),
                conversacion.getMensajes().stream().map(MensajeRespuesta::de).toList(),
                incluirToken ? conversacion.getTokenAcceso().toString() : null);
    }
}
