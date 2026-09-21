package com.retailstore.api.ubigeo.web;

import com.retailstore.api.ubigeo.dto.UbigeoRespuesta;
import com.retailstore.api.ubigeo.servicio.ServicioUbigeo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ubigeo público.
 *
 * <p>Sin sesión a propósito: el formulario de dirección se rellena durante el
 * registro y durante el checkout de un invitado, y en ninguno de los dos hay
 * todavía un cliente autenticado.
 */
@RestController
@RequestMapping("/api/v1/ubigeo")
@Tag(name = "Ubigeo")
public class UbigeoControlador {

    private final ServicioUbigeo servicio;

    public UbigeoControlador(ServicioUbigeo servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/departamentos")
    @Operation(summary = "Los 25 departamentos, por nombre")
    public List<UbigeoRespuesta> departamentos() {
        return servicio.departamentos();
    }

    @GetMapping("/departamentos/{id}/provincias")
    @Operation(summary = "Provincias de un departamento")
    public List<UbigeoRespuesta> provincias(@PathVariable Short id) {
        return servicio.provinciasDe(id);
    }

    @GetMapping("/provincias/{id}/distritos")
    @Operation(summary = "Distritos de una provincia")
    public List<UbigeoRespuesta> distritos(@PathVariable Short id) {
        return servicio.distritosDe(id);
    }
}
