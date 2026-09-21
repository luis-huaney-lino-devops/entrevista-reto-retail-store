package com.retailstore.api.catalogo.web;

import com.retailstore.api.catalogo.dto.ActualizarMarcaPeticion;
import com.retailstore.api.catalogo.dto.CambiarEstadoPeticion;
import com.retailstore.api.catalogo.dto.CrearMarcaPeticion;
import com.retailstore.api.catalogo.dto.MarcaRespuesta;
import com.retailstore.api.catalogo.servicio.ServicioMarca;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/marcas")
@Tag(name = "Marcas (panel)")
@SecurityRequirement(name = "tokenPanel")
@Validated
public class MarcaAdminControlador {

    private final ServicioMarca servicio;

    public MarcaAdminControlador(ServicioMarca servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listado de marcas, activas e inactivas")
    public RespuestaPagina<MarcaRespuesta> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser al menos 1") int pagina,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = 100, message = "no puede superar 100") int tamanoPagina) {
        return servicio.listar(texto, pagina, tamanoPagina);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una marca")
    public MarcaRespuesta porId(@PathVariable Long id) {
        return servicio.porId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una marca")
    public MarcaRespuesta crear(@Valid @RequestBody CrearMarcaPeticion peticion) {
        return servicio.crear(peticion);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una marca")
    public MarcaRespuesta actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarMarcaPeticion peticion) {
        return servicio.actualizar(id, peticion);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar")
    public MarcaRespuesta cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoPeticion peticion) {
        return servicio.cambiarEstado(id, peticion.activo());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminación lógica: la fila se conserva con quién y cuándo")
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }
}
