package com.retailstore.api.catalogo.web;

import com.retailstore.api.catalogo.dto.ActualizarSubcategoriaPeticion;
import com.retailstore.api.catalogo.dto.CambiarEstadoPeticion;
import com.retailstore.api.catalogo.dto.CrearSubcategoriaPeticion;
import com.retailstore.api.catalogo.dto.SubcategoriaRespuesta;
import com.retailstore.api.catalogo.servicio.ServicioSubcategoria;
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
@RequestMapping("/api/v1/admin/subcategorias")
@Tag(name = "Subcategorías (panel)")
@SecurityRequirement(name = "tokenPanel")
@Validated
public class SubcategoriaAdminControlador {

    private final ServicioSubcategoria servicio;

    public SubcategoriaAdminControlador(ServicioSubcategoria servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listado de subcategorías de toda la tienda")
    public RespuestaPagina<SubcategoriaRespuesta> listar(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser al menos 1") int pagina,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = 200, message = "no puede superar 200") int tamanoPagina) {
        return servicio.listar(pagina, tamanoPagina);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una subcategoría")
    public SubcategoriaRespuesta porId(@PathVariable Long id) {
        return servicio.porId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una subcategoría dentro de una categoría")
    public SubcategoriaRespuesta crear(@Valid @RequestBody CrearSubcategoriaPeticion peticion) {
        return servicio.crear(peticion);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una subcategoría; admite moverla de categoría")
    public SubcategoriaRespuesta actualizar(@PathVariable Long id,
                                            @Valid @RequestBody ActualizarSubcategoriaPeticion peticion) {
        return servicio.actualizar(id, peticion);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar; falla si tiene productos activos")
    public SubcategoriaRespuesta cambiarEstado(@PathVariable Long id,
                                               @Valid @RequestBody CambiarEstadoPeticion peticion) {
        return servicio.cambiarEstado(id, peticion.activo());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminación lógica: la fila se conserva con quién y cuándo")
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }
}
