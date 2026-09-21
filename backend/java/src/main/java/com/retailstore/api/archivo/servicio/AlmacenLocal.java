package com.retailstore.api.archivo.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Almacén en disco, para desarrollo.
 *
 * <p>Existe para que levantar el proyecto no exija una cuenta de Cloudflare ni
 * credenciales compartidas. Sirve los archivos {@link ArchivoLocalControlador}.
 */
public class AlmacenLocal implements AlmacenObjetos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenLocal.class);

    private final Path raiz;
    private final String urlBase;

    public AlmacenLocal(Path raiz, String urlBase) {
        this.raiz = raiz.toAbsolutePath().normalize();
        this.urlBase = urlBase;
        try {
            Files.createDirectories(this.raiz);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo crear el directorio de archivos: " + this.raiz, ex);
        }
    }

    @Override
    public void guardar(String clave, byte[] contenido, String tipoMime) {
        Path destino = resolver(clave);
        try {
            Files.createDirectories(destino.getParent());
            // Escritura atómica: si el proceso muere a mitad, no queda un
            // archivo truncado que después se sirva como imagen rota.
            Path temporal = Files.createTempFile(destino.getParent(), "subiendo-", ".tmp");
            Files.write(temporal, contenido);
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("No se pudo guardar {}", destino, ex);
            throw new ExcepcionAplicacion(CodigoError.SERVICE_UNAVAILABLE, "No se pudo guardar el archivo.");
        }
    }

    @Override
    public void borrar(String clave) {
        try {
            Files.deleteIfExists(resolver(clave));
        } catch (IOException ex) {
            // Un borrado fallido deja un archivo huérfano: ocupa espacio y no
            // rompe nada. Se registra y no se propaga.
            log.warn("No se pudo borrar {}", clave, ex);
        }
    }

    @Override
    public String urlPublica(String clave) {
        return urlBase + "/" + clave;
    }

    /** Lectura para el controlador que sirve los archivos en desarrollo. */
    public byte[] leer(String clave) {
        try {
            Path origen = resolver(clave);
            return Files.exists(origen) ? Files.readAllBytes(origen) : null;
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Resuelve la clave dentro de la raíz y comprueba que no se sale.
     *
     * <p>Sin esta comprobación, una clave con {@code ../} escribe o lee fuera
     * del directorio. Hoy las claves las genera el servidor y nunca contienen
     * eso, pero este método también recibe lo que llega por URL al servir un
     * archivo, y eso sí lo elige quien pide.
     */
    private Path resolver(String clave) {
        Path destino = raiz.resolve(clave).normalize();
        if (!destino.startsWith(raiz)) {
            throw new ExcepcionAplicacion(CodigoError.FILE_NOT_FOUND, "Ruta de archivo inválida.");
        }
        return destino;
    }
}
