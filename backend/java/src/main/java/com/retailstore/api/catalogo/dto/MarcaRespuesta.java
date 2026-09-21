package com.retailstore.api.catalogo.dto;

import com.retailstore.api.archivo.dto.ArchivoRespuesta;
import com.retailstore.api.catalogo.dominio.Marca;

public record MarcaRespuesta(
        Long id,
        String nombre,
        String slug,
        String descripcion,
        ArchivoRespuesta logo,
        boolean activa) {

    public static MarcaRespuesta de(Marca marca) {
        return new MarcaRespuesta(
                marca.getId(),
                marca.getNombre(),
                marca.getSlug(),
                marca.getDescripcion(),
                marca.getLogo() == null ? null : ArchivoRespuesta.de(marca.getLogo()),
                marca.isActiva());
    }
}
