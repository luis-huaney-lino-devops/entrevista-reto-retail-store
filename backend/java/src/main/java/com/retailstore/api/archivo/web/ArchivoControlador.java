package com.retailstore.api.archivo.web;

import com.retailstore.api.archivo.dto.ArchivoRespuesta;
import com.retailstore.api.archivo.servicio.ServicioArchivo;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/archivos")
@Tag(name = "Archivos")
@SecurityRequirement(name = "tokenPanel")
@Validated
public class ArchivoControlador {

    private final ServicioArchivo servicio;

    public ArchivoControlador(ServicioArchivo servicio) {
        this.servicio = servicio;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subir una imagen: se convierte a WebP y se generan cuatro tamaños")
    public ArchivoRespuesta subir(@RequestParam("archivo") MultipartFile archivo,
                                  @RequestParam("textoAlt") String textoAlt) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR, "Falta el archivo.");
        }
        try {
            return servicio.subir(archivo.getBytes(), archivo.getOriginalFilename(), textoAlt);
        } catch (IOException ex) {
            // El flujo se cortó a mitad de la subida: es un fallo de transporte,
            // no un archivo inválido.
            throw new ExcepcionAplicacion(CodigoError.MALFORMED_REQUEST, "No se pudo leer el archivo subido.");
        }
    }

    @GetMapping
    @Operation(summary = "Listado de archivos, del más reciente al más antiguo")
    public RespuestaPagina<ArchivoRespuesta> listar(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser al menos 1") int pagina,
            @RequestParam(defaultValue = "24")
            @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = 100, message = "no puede superar 100") int tamanoPagina) {
        return servicio.listar(pagina, tamanoPagina);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un archivo")
    public ArchivoRespuesta porId(@PathVariable Long id) {
        return servicio.porId(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar un archivo que no esté en uso")
    public void borrar(@PathVariable Long id) {
        servicio.borrar(id);
    }
}
