package com.retailstore.api.catalogo.dto;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.archivo.dominio.TamanoVariante;

/**
 * Una imagen de la galería, con los tamaños que la página necesita para armar
 * un {@code srcset} sin calcular URLs.
 */
public record ImagenRespuesta(Long archivoId, String miniatura, String tarjeta, String detalle,
                              String original, String textoAlt, int ancho, int alto) {

    public static ImagenRespuesta de(Archivo archivo) {
        return new ImagenRespuesta(
                archivo.getId(),
                archivo.urlDe(TamanoVariante.MINIATURA),
                archivo.urlDe(TamanoVariante.TARJETA),
                archivo.urlDe(TamanoVariante.DETALLE),
                archivo.urlDe(TamanoVariante.ORIGINAL),
                archivo.getTextoAlt(),
                archivo.getAncho(),
                archivo.getAlto());
    }
}
