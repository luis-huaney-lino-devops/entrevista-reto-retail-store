package com.retailstore.api.ubigeo.dto;

import com.retailstore.api.ubigeo.dominio.Departamento;
import com.retailstore.api.ubigeo.dominio.Distrito;
import com.retailstore.api.ubigeo.dominio.Provincia;

/**
 * Un nivel del ubigeo: lo justo para pintar un desplegable.
 *
 * <p>El mismo record para los tres niveles, y el id siempre {@code Long}: en la
 * base son {@code smallint} y {@code integer} por tamaño de tabla, pero el
 * cliente no tiene por qué saber cuál es cuál ni cambiar de tipo al bajar de
 * departamento a distrito.
 */
public record UbigeoRespuesta(Long id, String nombre) {

    public static UbigeoRespuesta de(Departamento departamento) {
        return new UbigeoRespuesta(departamento.getId().longValue(), departamento.getNombre());
    }

    public static UbigeoRespuesta de(Provincia provincia) {
        return new UbigeoRespuesta(provincia.getId().longValue(), provincia.getNombre());
    }

    public static UbigeoRespuesta de(Distrito distrito) {
        return new UbigeoRespuesta(distrito.getId().longValue(), distrito.getNombre());
    }
}
