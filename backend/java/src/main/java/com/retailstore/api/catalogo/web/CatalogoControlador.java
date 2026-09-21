package com.retailstore.api.catalogo.web;

import com.retailstore.api.catalogo.dto.BusquedaProductoPeticion;
import com.retailstore.api.catalogo.dto.CategoriaRespuesta;
import com.retailstore.api.catalogo.dto.MarcaRespuesta;
import com.retailstore.api.catalogo.dto.ProductoDetalleRespuesta;
import com.retailstore.api.catalogo.dto.ProductoResumenRespuesta;
import com.retailstore.api.catalogo.dto.SubcategoriaRespuesta;
import com.retailstore.api.catalogo.servicio.ServicioCategoria;
import com.retailstore.api.catalogo.servicio.ServicioMarca;
import com.retailstore.api.catalogo.servicio.ServicioProducto;
import com.retailstore.api.catalogo.servicio.ServicioSubcategoria;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo público. Sin autenticación: un catálogo detrás de un login no es una
 * tienda.
 *
 * <p>Todo lo que devuelve está filtrado por visibilidad: producto activo, en
 * subcategoría activa, de categoría activa. Un producto despublicado es 404
 * aquí y 200 en el panel.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catálogo (tienda)")
public class CatalogoControlador {

    private final ServicioProducto productos;
    private final ServicioCategoria categorias;
    private final ServicioSubcategoria subcategorias;
    private final ServicioMarca marcas;

    public CatalogoControlador(ServicioProducto productos, ServicioCategoria categorias,
                               ServicioSubcategoria subcategorias, ServicioMarca marcas) {
        this.productos = productos;
        this.categorias = categorias;
        this.subcategorias = subcategorias;
        this.marcas = marcas;
    }

    // ---------------------------------------------------------- productos

    @GetMapping("/productos")
    @Operation(summary = "Listado con búsqueda, filtros, orden y paginación")
    public RespuestaPagina<ProductoResumenRespuesta> buscar(
            @Valid @ParameterObject BusquedaProductoPeticion peticion) {
        return productos.buscar(peticion);
    }

    @GetMapping("/productos/{slug}")
    @Operation(summary = "Detalle de un producto por slug")
    public ProductoDetalleRespuesta porSlug(@PathVariable String slug) {
        return productos.porSlug(slug);
    }

    @GetMapping("/productos/{slug}/relacionados")
    @Operation(summary = "Hasta 4 productos de la misma subcategoría")
    public List<ProductoResumenRespuesta> relacionados(@PathVariable String slug) {
        return productos.relacionados(slug);
    }

    // --------------------------------------------------------- navegación

    @GetMapping("/categorias")
    @Operation(summary = "Menú completo: categorías activas con sus subcategorías visibles")
    public List<CategoriaRespuesta> menu() {
        return categorias.menu();
    }

    @GetMapping("/categorias/{slug}")
    @Operation(summary = "Una categoría con sus subcategorías activas")
    public CategoriaRespuesta categoria(@PathVariable String slug) {
        return categorias.porSlug(slug);
    }

    @GetMapping("/subcategorias/{slug}")
    @Operation(summary = "Una subcategoría por slug")
    public SubcategoriaRespuesta subcategoria(@PathVariable String slug) {
        return subcategorias.porSlug(slug);
    }

    @GetMapping("/marcas")
    @Operation(summary = "Marcas activas, para el filtro de la tienda")
    public List<MarcaRespuesta> marcas() {
        return marcas.visibles();
    }

    @GetMapping("/marcas/{slug}")
    @Operation(summary = "Una marca por slug")
    public MarcaRespuesta marca(@PathVariable String slug) {
        return marcas.porSlug(slug);
    }
}
