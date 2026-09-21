package com.retailstore.api.catalogo.servicio;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.archivo.servicio.ServicioArchivo;
import com.retailstore.api.catalogo.dominio.Marca;
import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.catalogo.dominio.Subcategoria;
import com.retailstore.api.catalogo.dto.ActualizarProductoPeticion;
import com.retailstore.api.catalogo.dto.BusquedaProductoPeticion;
import com.retailstore.api.catalogo.dto.CrearProductoPeticion;
import com.retailstore.api.catalogo.dto.ProductoAdminRespuesta;
import com.retailstore.api.catalogo.dto.ProductoDetalleRespuesta;
import com.retailstore.api.catalogo.dto.ProductoResumenRespuesta;
import com.retailstore.api.catalogo.repositorio.MarcaRepositorio;
import com.retailstore.api.catalogo.repositorio.ProductoRepositorio;
import com.retailstore.api.catalogo.repositorio.ProductoSpecs;
import com.retailstore.api.catalogo.repositorio.SubcategoriaRepositorio;
import com.retailstore.api.comun.auditoria.UsuarioActual;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.comun.texto.Slug;
import com.retailstore.api.notificacion.servicio.AvisosDeStock;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServicioProducto {

    private static final int MAXIMO_RELACIONADOS = 4;

    private final ProductoRepositorio productos;
    private final SubcategoriaRepositorio subcategorias;
    private final MarcaRepositorio marcas;
    private final ServicioArchivo archivos;
    private final AvisosDeStock avisos;
    private final Clock reloj;

    public ServicioProducto(ProductoRepositorio productos, SubcategoriaRepositorio subcategorias,
                            MarcaRepositorio marcas, ServicioArchivo archivos,
                            AvisosDeStock avisos, Clock reloj) {
        this.productos = productos;
        this.subcategorias = subcategorias;
        this.marcas = marcas;
        this.archivos = archivos;
        this.avisos = avisos;
        this.reloj = reloj;
    }

    // ============================================================ panel

    /** El panel ve todo, publicado o no, y puede filtrar por estado. */
    @Transactional(readOnly = true)
    public RespuestaPagina<ProductoAdminRespuesta> listarAdmin(BusquedaProductoPeticion peticion, Boolean activo) {
        validarRangoPrecios(peticion);

        List<Specification<Producto>> filtros = new ArrayList<>();
        if (tieneTexto(peticion.texto())) {
            filtros.add(ProductoSpecs.coincideTextoOSku(peticion.texto()));
        }
        if (activo != null) {
            filtros.add(activo ? ProductoSpecs.estaActivo() : ProductoSpecs.estaInactivo());
        }
        anadirFiltrosComunes(peticion, filtros);

        Page<Producto> pagina = productos.findAll(
                Specification.allOf(filtros), paginacion(peticion));
        return RespuestaPagina.de(pagina, ProductoAdminRespuesta::de);
    }

    @Transactional(readOnly = true)
    public ProductoAdminRespuesta porIdAdmin(Long id) {
        return ProductoAdminRespuesta.de(buscarConDetalle(id));
    }

    public ProductoAdminRespuesta crear(CrearProductoPeticion peticion) {
        String sku = peticion.sku().trim().toUpperCase(Locale.ROOT);
        if (productos.existsBySku(sku)) {
            throw new ExcepcionAplicacion(CodigoError.DUPLICATE_SKU,
                    "Ya existe un producto con el SKU '" + sku + "'.")
                    .con("sku", sku);
        }

        String nombre = peticion.nombre().trim();
        Producto producto = new Producto(
                sku,
                nombre,
                Slug.unico(nombre, productos::existsBySlug),
                subcategoria(peticion.subcategoriaId()),
                peticion.precio(),
                peticion.stock());

        producto.cambiarPrecios(peticion.precio(), peticion.precioAnterior());
        producto.editarDatos(
                nombre,
                textoODefecto(peticion.descripcionCorta()),
                textoODefecto(peticion.descripcion()),
                producto.getSubcategoria(),
                marca(peticion.marcaId()),
                peticion.stock(),
                peticion.destacado());
        producto.reemplazarImagenes(imagenes(peticion.imagenIds()));

        // Nace inactivo aunque venga con fotos: publicar es una decisión
        // explícita, no un efecto secundario de crear.
        //
        // Aquí save() y no flush() a secas: la entidad todavía es transitoria y
        // nadie la ha metido en la sesión, así que volcar no escribiría nada y
        // la respuesta saldría sin id. En lo que ya está gestionado es al revés
        // —save() haría merge, que persiste una copia de los hijos nuevos.
        Producto guardado = productos.save(producto);
        productos.flush();
        avisos.revisar(guardado);
        return ProductoAdminRespuesta.de(guardado);
    }

    public ProductoAdminRespuesta actualizar(Long id, ActualizarProductoPeticion peticion) {
        Producto producto = buscarConDetalle(id);

        producto.editarDatos(
                peticion.nombre().trim(),
                textoODefecto(peticion.descripcionCorta()),
                textoODefecto(peticion.descripcion()),
                subcategoria(peticion.subcategoriaId()),
                marca(peticion.marcaId()),
                peticion.stock(),
                peticion.destacado());
        producto.cambiarPrecios(peticion.precio(), peticion.precioAnterior());
        producto.reemplazarImagenes(imagenes(peticion.imagenIds()));

        // Volcar antes de mapear: la auditoría y la versión las escribe
        // Hibernate al volcar, no al mutar la entidad.
        productos.flush();
        Producto guardado = producto;
        avisos.revisar(guardado);
        return ProductoAdminRespuesta.de(guardado);
    }

    public ProductoAdminRespuesta cambiarEstado(Long id, boolean activo) {
        Producto producto = buscarConDetalle(id);
        if (activo) {
            producto.activar();
        } else {
            producto.desactivar();
        }
        productos.flush();
        Producto guardado = producto;
        avisos.revisar(guardado);
        return ProductoAdminRespuesta.de(guardado);
    }

    // =========================================================== tienda

    @Transactional(readOnly = true)
    public RespuestaPagina<ProductoResumenRespuesta> buscar(BusquedaProductoPeticion peticion) {
        validarRangoPrecios(peticion);

        List<Specification<Producto>> filtros = new ArrayList<>();
        // RN-020: la tienda solo muestra lo publicable, y eso incluye que su
        // subcategoría y su categoría estén activas.
        filtros.add(ProductoSpecs.esVisibleEnTienda());
        if (tieneTexto(peticion.texto())) {
            filtros.add(ProductoSpecs.coincideTexto(peticion.texto()));
        }
        anadirFiltrosComunes(peticion, filtros);

        Page<Producto> pagina = productos.findAll(Specification.allOf(filtros), paginacion(peticion));
        return RespuestaPagina.de(pagina, ProductoResumenRespuesta::de);
    }

    /**
     * Ficha de producto de la tienda. <strong>Registra la visita.</strong>
     *
     * <p>No es readOnly por eso: abrir una ficha escribe. El incremento va en
     * un UPDATE directo, no tocando la entidad, para que el bloqueo optimista
     * no considere una visita una modificación del producto.
     */
    public ProductoDetalleRespuesta porSlug(String slug) {
        Producto producto = buscarVisible(slug);
        productos.sumarVista(producto.getId());
        return ProductoDetalleRespuesta.de(producto);
    }

    /** RN-025: hasta 4 de la misma subcategoría, excluyendo el actual. */
    @Transactional(readOnly = true)
    public List<ProductoResumenRespuesta> relacionados(String slug) {
        Producto producto = buscarVisible(slug);
        return productos.relacionados(
                        producto.getSubcategoria().getId(),
                        producto.getId(),
                        PageRequest.of(0, MAXIMO_RELACIONADOS))
                .stream()
                .map(ProductoResumenRespuesta::de)
                .toList();
    }

    // ============================================================ apoyo

    private void anadirFiltrosComunes(BusquedaProductoPeticion peticion, List<Specification<Producto>> filtros) {
        if (tieneTexto(peticion.categoria())) {
            filtros.add(ProductoSpecs.enCategoriaPorSlug(peticion.categoria().trim()));
        }
        if (tieneTexto(peticion.subcategoria())) {
            filtros.add(ProductoSpecs.enSubcategoriaPorSlug(peticion.subcategoria().trim()));
        }
        if (tieneTexto(peticion.marca())) {
            filtros.add(ProductoSpecs.deMarcaPorSlug(peticion.marca().trim()));
        }
        if (peticion.precioMinimo() != null) {
            filtros.add(ProductoSpecs.precioDesde(peticion.precioMinimo()));
        }
        if (peticion.precioMaximo() != null) {
            filtros.add(ProductoSpecs.precioHasta(peticion.precioMaximo()));
        }
        if (Boolean.TRUE.equals(peticion.conStock())) {
            filtros.add(ProductoSpecs.conStock());
        }
        if (Boolean.TRUE.equals(peticion.destacado())) {
            filtros.add(ProductoSpecs.esDestacado());
        }
    }

    private static Pageable paginacion(BusquedaProductoPeticion peticion) {
        return PageRequest.of(
                peticion.paginaEfectiva() - 1,
                peticion.tamanoEfectivo(),
                OrdenProducto.desde(peticion.orden()).orden());
    }

    private static void validarRangoPrecios(BusquedaProductoPeticion peticion) {
        BigDecimal minimo = peticion.precioMinimo();
        BigDecimal maximo = peticion.precioMaximo();
        if (minimo != null && maximo != null && minimo.compareTo(maximo) > 0) {
            throw new ExcepcionAplicacion(CodigoError.INVALID_PRICE_RANGE,
                    "El precio mínimo no puede ser mayor que el máximo.")
                    .con("precioMinimo", minimo)
                    .con("precioMaximo", maximo);
        }
    }

    private Producto buscarVisible(String slug) {
        Producto producto = productos.findBySlugAndActivoTrue(slug).orElseThrow(() -> noEncontrado(slug));
        if (!producto.getSubcategoria().esVisible()) {
            // Para el comprador no existe. Decirle «existe pero está oculto»
            // filtraría el catálogo interno.
            throw noEncontrado(slug);
        }
        return producto;
    }

    private Producto buscarConDetalle(Long id) {
        return productos.findConDetalleById(id).orElseThrow(() -> noEncontrado(String.valueOf(id)));
    }

    /**
     * Eliminación lógica (RN-007, RN-086): el producto sale del catálogo y del
     * panel, pero la fila se queda. Un producto referenciado por órdenes no
     * puede desaparecer sin romper el histórico, y además así se sabe quién lo
     * quitó y cuándo.
     */
    public void eliminar(Long id) {
        Producto producto = buscarConDetalle(id);
        producto.desactivar();
        producto.eliminar(reloj.instant(), UsuarioActual.nombre());
        // Un producto eliminado ya no pierde ventas: sus avisos se retiran.
        avisos.revisar(producto);
    }

    private static ExcepcionAplicacion noEncontrado(String referencia) {
        return new ExcepcionAplicacion(CodigoError.PRODUCT_NOT_FOUND,
                "No existe el producto '" + referencia + "'.");
    }

    private Subcategoria subcategoria(Long id) {
        Subcategoria subcategoria = subcategorias.findById(id)
                .filter(candidata -> !candidata.estaEliminado())
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.SUBCATEGORY_NOT_FOUND,
                        "No existe la subcategoría " + id + "."));
        if (!subcategoria.isActiva()) {
            // RN-001: asignar un producto a una subcategoría inactiva lo deja
            // inalcanzable desde la tienda sin que nadie lo note.
            throw new ExcepcionAplicacion(CodigoError.SUBCATEGORY_INACTIVE,
                    "La subcategoría '" + subcategoria.getNombre() + "' está inactiva.")
                    .con("subcategoriaId", subcategoria.getId())
                    .con("subcategoriaNombre", subcategoria.getNombre());
        }
        return subcategoria;
    }

    private Marca marca(Long id) {
        if (id == null) {
            return null;
        }
        return marcas.findById(id)
                .filter(marca -> !marca.estaEliminado())
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.BRAND_NOT_FOUND,
                        "No existe la marca " + id + "."));
    }

    private List<Archivo> imagenes(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // Se resuelven todos antes de tocar el producto: si uno no existe, la
        // galería no queda a medio reemplazar.
        return ids.stream().distinct().map(archivos::referencia).toList();
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String textoODefecto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
