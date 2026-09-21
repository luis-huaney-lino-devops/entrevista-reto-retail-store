package com.retailstore.api.catalogo.web;

import com.retailstore.api.catalogo.dto.ActualizarProductoPeticion;
import com.retailstore.api.catalogo.dto.BusquedaProductoPeticion;
import com.retailstore.api.catalogo.dto.CambiarDestacadoPeticion;
import com.retailstore.api.catalogo.dto.CambiarEstadoPeticion;
import com.retailstore.api.catalogo.dto.CrearProductoPeticion;
import com.retailstore.api.catalogo.dto.ProductoAdminRespuesta;
import com.retailstore.api.catalogo.servicio.ServicioProducto;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
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

/**
 * Productos desde el panel.
 *
 * <p>Ve todo el catálogo, publicado o no, y devuelve el SKU y la auditoría, que
 * la tienda no recibe.
 */
@RestController
@RequestMapping("/api/v1/admin/productos")
@Tag(name = "Productos (panel)")
@SecurityRequirement(name = "tokenPanel")
public class ProductoAdminControlador {

    private final ServicioProducto servicio;

    public ProductoAdminControlador(ServicioProducto servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listado con búsqueda por nombre o SKU y filtro por estado")
    public RespuestaPagina<ProductoAdminRespuesta> listar(
            @Valid @ParameterObject BusquedaProductoPeticion peticion,
            @RequestParam(required = false) Boolean activo) {
        return servicio.listarAdmin(peticion, activo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un producto por id")
    public ProductoAdminRespuesta porId(@PathVariable Long id) {
        return servicio.porIdAdmin(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un producto. Nace inactivo: publicar es un PATCH aparte")
    public ProductoAdminRespuesta crear(@Valid @RequestBody CrearProductoPeticion peticion) {
        return servicio.crear(peticion);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un producto. El SKU no se puede cambiar")
    public ProductoAdminRespuesta actualizar(@PathVariable Long id,
                                             @Valid @RequestBody ActualizarProductoPeticion peticion) {
        return servicio.actualizar(id, peticion);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Publicar o despublicar. Publicar exige al menos una imagen")
    public ProductoAdminRespuesta cambiarEstado(@PathVariable Long id,
                                                @Valid @RequestBody CambiarEstadoPeticion peticion) {
        return servicio.cambiarEstado(id, peticion.activo());
    }

    @PatchMapping("/{id}/destacado")
    @Operation(summary = "Destacar en portada o quitar de destacados")
    public ProductoAdminRespuesta cambiarDestacado(@PathVariable Long id,
                                                   @Valid @RequestBody CambiarDestacadoPeticion peticion) {
        return servicio.cambiarDestacado(id, peticion.destacado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminación lógica: sale del catálogo y del panel, pero la fila se conserva")
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }
}
