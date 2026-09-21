package com.retailstore.api.cuenta.dto;

import com.retailstore.api.cuenta.dominio.DireccionCliente;
import com.retailstore.api.ubigeo.dominio.Distrito;
import com.retailstore.api.ubigeo.dto.UbigeoRespuesta;
import java.math.BigDecimal;

/**
 * Una dirección con los tres niveles del ubigeo ya resueltos.
 *
 * <p>Se devuelven los nombres y no solo los ids para que pintar la lista no
 * obligue a la tienda a pedir tres catálogos y cruzarlos a mano.
 */
public record DireccionRespuesta(
        Long id,
        String etiqueta,
        String destinatario,
        String telefono,
        String calle,
        String numero,
        String referencia,
        String codigoPostal,
        BigDecimal latitud,
        BigDecimal longitud,
        boolean predeterminada,
        UbigeoRespuesta distrito,
        UbigeoRespuesta provincia,
        UbigeoRespuesta departamento) {

    public static DireccionRespuesta de(DireccionCliente direccion) {
        Distrito distrito = direccion.getDistrito();
        return new DireccionRespuesta(
                direccion.getId(),
                direccion.getEtiqueta(),
                direccion.getDestinatario(),
                direccion.getTelefono(),
                direccion.getCalle(),
                direccion.getNumero(),
                direccion.getReferencia(),
                direccion.getCodigoPostal(),
                direccion.getLatitud(),
                direccion.getLongitud(),
                direccion.isPredeterminada(),
                UbigeoRespuesta.de(distrito),
                UbigeoRespuesta.de(distrito.getProvincia()),
                UbigeoRespuesta.de(distrito.getProvincia().getDepartamento()));
    }
}
