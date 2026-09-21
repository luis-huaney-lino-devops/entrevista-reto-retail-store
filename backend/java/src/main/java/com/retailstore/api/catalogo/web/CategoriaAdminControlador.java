package com.retailstore.api.catalogo.web;

import com.retailstore.api.catalogo.dto.ActualizarCategoriaPeticion;
import com.retailstore.api.catalogo.dto.CambiarEstadoPeticion;
import com.retailstore.api.catalogo.dto.CategoriaRespuesta;
import com.retailstore.api.catalogo.dto.CrearCategoriaPeticion;
import com.retailstore.api.catalogo.dto.SubcategoriaRespuesta;
import com.retailstore.api.catalogo.servicio.ServicioCategoria;
import com.retailstore.api.catalogo.servicio.ServicioSubcategoria;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
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
@RequestMapping("/api/v1/admin/categorias")
@Tag(name = "Categorías (panel)")
@SecurityRequirement(name = "tokenPanel")
@Validated
public class CategoriaAdminControlador {

    private final ServicioCategoria servicio;
    private final ServicioSubcategoria subcategorias;

    public CategoriaAdminControlador(ServicioCategoria servicio, ServicioSubcategoria subcategorias) {
        this.servicio = servicio;
        this.subcategorias = subcategorias;
    }

    @GetMapping
    @Operation(summary = "Listado de categorías")
    public RespuestaPagina<CategoriaRespuesta> listar(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser al menos 1") int pagina,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = 100, message = "no puede superar 100") int tamanoPagina) {
        return servicio.listar(pagina, tamanoPagina);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una categoría, con sus subcategorías")
    public CategoriaRespuesta porId(@PathVariable Long id) {
        return servicio.porId(id);
    }

    @GetMapping("/{id}/subcategorias")
    @Operation(summary = "Subcategorías de una categoría")
    public List<SubcategoriaRespuesta> subcategoriasDe(@PathVariable Long id) {
        return subcategorias.deCategoria(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una categoría")
    public CategoriaRespuesta crear(@Valid @RequestBody CrearCategoriaPeticion peticion) {
        return servicio.crear(peticion);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una categoría")
    public CategoriaRespuesta actualizar(@PathVariable Long id,
                                         @Valid @RequestBody ActualizarCategoriaPeticion peticion) {
        return servicio.actualizar(id, peticion);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar; falla si tiene subcategorías activas")
    public CategoriaRespuesta cambiarEstado(@PathVariable Long id,
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
