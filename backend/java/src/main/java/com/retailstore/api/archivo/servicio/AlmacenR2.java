package com.retailstore.api.archivo.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Cloudflare R2 a través del protocolo de S3 (ADR-0009).
 *
 * <p>Lo que R2 aporta frente a S3 para una tienda: <strong>no cobra por
 * egreso</strong>. Las imágenes se descargan muchas más veces de las que se
 * suben, y el egreso es justo lo que encarece S3.
 */
public class AlmacenR2 implements AlmacenObjetos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenR2.class);

    /**
     * La clave incluye el hash del contenido, así que si el contenido cambia
     * cambia la clave. Eso permite cachear para siempre sin miedo a servir algo
     * obsoleto.
     */
    private static final String CACHE_ETERNA = "public, max-age=31536000, immutable";

    private final S3Client cliente;
    private final String bucket;
    private final String urlBase;

    public AlmacenR2(S3Client cliente, String bucket, String urlBase) {
        this.cliente = cliente;
        this.bucket = bucket;
        this.urlBase = urlBase;
    }

    @Override
    public void guardar(String clave, byte[] contenido, String tipoMime) {
        try {
            cliente.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(clave)
                            .contentType(tipoMime)
                            .cacheControl(CACHE_ETERNA)
                            .build(),
                    RequestBody.fromBytes(contenido));
        } catch (S3Exception | software.amazon.awssdk.core.exception.SdkClientException ex) {
            log.error("Fallo al subir {} a R2", clave, ex);
            throw new ExcepcionAplicacion(CodigoError.SERVICE_UNAVAILABLE,
                    "No se pudo guardar la imagen. Inténtalo de nuevo.");
        }
    }

    @Override
    public void borrar(String clave) {
        try {
            cliente.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave).build());
        } catch (RuntimeException ex) {
            // Objeto huérfano: ocupa espacio y no rompe nada. Lo recoge la
            // limpieza programada.
            log.warn("No se pudo borrar {} de R2", clave, ex);
        }
    }

    @Override
    public String urlPublica(String clave) {
        return urlBase + "/" + clave;
    }
}
