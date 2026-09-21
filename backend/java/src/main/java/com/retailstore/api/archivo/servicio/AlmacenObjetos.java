package com.retailstore.api.archivo.servicio;

/**
 * Dónde viven los bytes de las imágenes.
 *
 * <p>La interfaz existe para que el desarrollo no necesite una cuenta de
 * Cloudflare, no para «poder cambiar de proveedor algún día». Esa segunda razón
 * casi nunca se cobra; la primera se cobra el primer día.
 *
 * <p>R2 habla el protocolo de S3, así que mover esto a S3, MinIO o Backblaze es
 * cambiar tres variables de entorno, no escribir otra implementación.
 */
public interface AlmacenObjetos {

    /**
     * Guarda el objeto. Idempotente: la misma clave con el mismo contenido se
     * puede escribir dos veces sin consecuencias, que es justo lo que pasa
     * cuando se deduplica por hash.
     */
    void guardar(String clave, byte[] contenido, String tipoMime);

    void borrar(String clave);

    /** URL con la que el navegador pedirá el objeto directamente, sin pasar por la API. */
    String urlPublica(String clave);
}
