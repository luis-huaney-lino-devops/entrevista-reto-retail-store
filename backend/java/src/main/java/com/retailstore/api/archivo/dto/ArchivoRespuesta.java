package com.retailstore.api.archivo.dto;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.archivo.dominio.VarianteArchivo;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un archivo tal como lo ven el panel y la tienda.
 *
 * <p>{@code urls} va indexado por nombre de variante para que el cliente pida
 * el tamaño que necesita sin construir rutas: si mañana cambian los anchos, el
 * frontend no se entera.
 */
public record ArchivoRespuesta(
        Long id,
        String url,
        Map<String, String> urls,
        String textoAlt,
        int ancho,
        int alto,
        long bytes,
        String nombreOriginal,
        Instant creadoEn) {

    public static ArchivoRespuesta de(Archivo archivo) {
        Map<String, String> urls = new LinkedHashMap<>();
        for (VarianteArchivo variante : archivo.getVariantes()) {
            urls.put(variante.getNombre().name(), variante.getUrlPublica());
        }
        return new ArchivoRespuesta(
                archivo.getId(),
                archivo.getUrlPublica(),
                urls,
                archivo.getTextoAlt(),
                archivo.getAncho(),
                archivo.getAlto(),
                archivo.getBytes(),
                archivo.getNombreOriginal(),
                archivo.getCreadoEn());
    }
}
