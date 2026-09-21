package com.retailstore.api.orden.web;

import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.orden.dto.CambiarEstadoOrdenPeticion;
import com.retailstore.api.orden.dto.OrdenDetalleRespuesta;
import com.retailstore.api.orden.dto.OrdenResumenRespuesta;
import com.retailstore.api.orden.servicio.ServicioOrden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ordenes")
@Tag(name = "Órdenes (panel)")
@SecurityRequirement(name = "tokenPanel")
@Validated
public class OrdenAdminControlador {

    private final ServicioOrden servicio;

    public OrdenAdminControlador(ServicioOrden servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listado de órdenes, de la más reciente a la más antigua")
    public RespuestaPagina<OrdenResumenRespuesta> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser al menos 1") int pagina,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = 100, message = "no puede superar 100") int tamanoPagina) {
        return servicio.listar(estado, pagina, tamanoPagina);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una orden con sus líneas")
    public OrdenDetalleRespuesta porId(@PathVariable Long id) {
        return servicio.porId(id);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Mover la orden de estado. Cancelar devuelve el stock")
    public OrdenDetalleRespuesta cambiarEstado(@PathVariable Long id,
                                               @Valid @RequestBody CambiarEstadoOrdenPeticion peticion) {
        return servicio.cambiarEstado(id, peticion.estado());
    }
}
