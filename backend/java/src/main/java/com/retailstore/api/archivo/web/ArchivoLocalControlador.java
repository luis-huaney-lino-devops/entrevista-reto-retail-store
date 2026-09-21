package com.retailstore.api.archivo.web;

import com.retailstore.api.archivo.servicio.AlmacenLocal;
import com.retailstore.api.archivo.servicio.AlmacenObjetos;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Sirve las imágenes en desarrollo.
 *
 * <p>Solo existe cuando el almacén es local. <strong>En producción el backend
 * no sirve imágenes</strong>: devuelve URLs y el navegador las pide
 * directamente a R2, en paralelo y sin consumir hilos de la API.
 */
@Controller
@Hidden
@ConditionalOnProperty(name = "app.almacen.tipo", havingValue = "local", matchIfMissing = true)
public class ArchivoLocalControlador {

    private final AlmacenLocal almacen;

    public ArchivoLocalControlador(AlmacenObjetos almacen) {
        // El @ConditionalOnProperty garantiza que aquí siempre llega el local.
        this.almacen = (AlmacenLocal) almacen;
    }

    @GetMapping("/archivos/**")
    @ResponseBody
    public ResponseEntity<byte[]> servir(HttpServletRequest peticion) {
        String ruta = (String) peticion.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String clave = ruta.substring("/archivos/".length());

        byte[] contenido = almacen.leer(clave);
        if (contenido == null) {
            return ResponseEntity.notFound().build();
        }
        boolean esPdf = clave.endsWith(".pdf");

        var respuesta = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(esPdf ? "application/pdf" : "image/webp"))
                // La clave lleva el hash del contenido: si cambia el archivo,
                // cambia la URL. Por eso se puede cachear para siempre.
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable())
                // Sin esto, un navegador puede decidir por su cuenta que el
                // contenido es otra cosa y tratarlo como tal. Es la defensa
                // que convierte un archivo con tipo declarado en un archivo
                // con tipo respetado.
                .header("X-Content-Type-Options", "nosniff");

        if (esPdf) {
            // Un PDF de un cliente se descarga, no se abre dentro de la
            // página: así no se ejecuta en el contexto de nuestro dominio.
            respuesta = respuesta.header("Content-Disposition", "attachment");
        }

        return respuesta.body(contenido);
    }
}
