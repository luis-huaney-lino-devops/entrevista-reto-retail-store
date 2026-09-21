package com.retailstore.api.cliente.web;

import com.retailstore.api.catalogo.dto.CambiarEstadoPeticion;
import com.retailstore.api.cliente.dto.ClienteDetalleRespuesta;
import com.retailstore.api.cliente.dto.ClienteRespuesta;
import com.retailstore.api.cliente.servicio.ServicioCliente;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
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
@RequestMapping("/api/v1/admin/clientes")
@Tag(name = "Clientes (panel)")
@SecurityRequirement(name = "tokenPanel")
@Validated
public class ClienteAdminControlador {

    private final ServicioCliente servicio;

    public ClienteAdminControlador(ServicioCliente servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listado de clientes con su número de órdenes y total comprado")
    public RespuestaPagina<ClienteRespuesta> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser al menos 1") int pagina,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = 100, message = "no puede superar 100") int tamanoPagina) {
        return servicio.listar(texto, pagina, tamanoPagina);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un cliente con su historial de órdenes")
    public ClienteDetalleRespuesta porId(@PathVariable Long id) {
        return servicio.porId(id);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar. Un cliente desactivado no puede entrar")
    public ClienteRespuesta cambiarEstado(@PathVariable Long id,
                                          @Valid @RequestBody CambiarEstadoPeticion peticion) {
        return servicio.cambiarEstado(id, peticion.activo());
    }
}
