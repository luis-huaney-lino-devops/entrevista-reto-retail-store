package com.retailstore.api.catalogo.servicio;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.archivo.servicio.ServicioArchivo;
import com.retailstore.api.catalogo.dominio.Categoria;
import com.retailstore.api.catalogo.dominio.Subcategoria;
import com.retailstore.api.catalogo.dto.ActualizarSubcategoriaPeticion;
import com.retailstore.api.catalogo.dto.CrearSubcategoriaPeticion;
import com.retailstore.api.catalogo.dto.SubcategoriaRespuesta;
import com.retailstore.api.catalogo.repositorio.CategoriaRepositorio;
import com.retailstore.api.catalogo.repositorio.SubcategoriaRepositorio;
import com.retailstore.api.comun.auditoria.UsuarioActual;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.comun.texto.Slug;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServicioSubcategoria {

    private final SubcategoriaRepositorio subcategorias;
    private final CategoriaRepositorio categorias;
    private final ServicioArchivo archivos;

    private final Clock reloj;

    public ServicioSubcategoria(SubcategoriaRepositorio subcategorias, CategoriaRepositorio categorias,
                                ServicioArchivo archivos, Clock reloj) {
        this.subcategorias = subcategorias;
        this.categorias = categorias;
        this.archivos = archivos;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------ panel

    @Transactional(readOnly = true)
    public RespuestaPagina<SubcategoriaRespuesta> listar(int pagina, int tamanoPagina) {
        return RespuestaPagina.de(
                subcategorias.findAllByOrderByCategoriaNombreAscOrdenAscNombreAsc(
                        PageRequest.of(pagina - 1, tamanoPagina)),
                SubcategoriaRespuesta::de);
    }

    @Transactional(readOnly = true)
    public List<SubcategoriaRespuesta> deCategoria(Long idCategoria) {
        return subcategorias.findAllByCategoriaIdOrderByOrdenAscNombreAsc(idCategoria)
                .stream().map(SubcategoriaRespuesta::de).toList();
    }

    @Transactional(readOnly = true)
    public SubcategoriaRespuesta porId(Long id) {
        return SubcategoriaRespuesta.de(buscar(id));
    }

    public SubcategoriaRespuesta crear(CrearSubcategoriaPeticion peticion) {
        Categoria categoria = categoria(peticion.categoriaId());
        String nombre = peticion.nombre().trim();
        exigirNombreLibre(categoria, nombre, null);

        // El slug lleva el nombre de la categoría porque «Accesorios» existe en
        // varias: sin el sufijo, la segunda sería accesorios-2 y la URL no
        // diría nada de dónde está.
        String baseSlug = nombre + " " + categoria.getNombre();

        Subcategoria subcategoria = new Subcategoria(
                categoria,
                nombre,
                Slug.unico(baseSlug, subcategorias::existsBySlug),
                textoODefecto(peticion.descripcion()),
                imagen(peticion.imagenId()),
                peticion.orden());
        return SubcategoriaRespuesta.de(subcategorias.save(subcategoria));
    }

    public SubcategoriaRespuesta actualizar(Long id, ActualizarSubcategoriaPeticion peticion) {
        Subcategoria subcategoria = buscar(id);
        Categoria categoria = categoria(peticion.categoriaId());
        String nombre = peticion.nombre().trim();
        exigirNombreLibre(categoria, nombre, subcategoria.getId());

        subcategoria.editar(categoria, nombre, textoODefecto(peticion.descripcion()),
                imagen(peticion.imagenId()), peticion.orden());
        cambiarEstado(subcategoria, peticion.activa());
        subcategorias.flush();
        return SubcategoriaRespuesta.de(subcategoria);
    }

    public SubcategoriaRespuesta cambiarEstado(Long id, boolean activa) {
        Subcategoria subcategoria = buscar(id);
        cambiarEstado(subcategoria, activa);
        subcategorias.flush();
        return SubcategoriaRespuesta.de(subcategoria);
    }

    /** Eliminación lógica (RN-086). No se puede si tiene productos activos. */
    public void eliminar(Long id) {
        Subcategoria subcategoria = buscar(id);
        Dependientes.exigirNinguno(
                subcategorias.contarProductosActivos(subcategoria.getId()),
                subcategorias.nombresDeProductosActivos(
                        subcategoria.getId(), PageRequest.of(0, Dependientes.MAXIMO_LISTADO)),
                "eliminar la subcategoría '" + subcategoria.getNombre() + "'",
                "productos");
        subcategoria.desactivar();
        subcategoria.eliminar(reloj.instant(), UsuarioActual.nombre());
    }

    // ----------------------------------------------------------------- tienda

    @Transactional(readOnly = true)
    public SubcategoriaRespuesta porSlug(String slug) {
        return SubcategoriaRespuesta.de(subcategorias.findBySlug(slug)
                .orElseThrow(() -> noEncontrada(slug)));
    }

    // ------------------------------------------------------------------ apoyo

    private void cambiarEstado(Subcategoria subcategoria, boolean activa) {
        if (activa) {
            if (!subcategoria.getCategoria().isActiva()) {
                // Activarla dentro de una categoría oculta la deja invisible de
                // todas formas: decirlo es mejor que aceptarlo en silencio.
                throw new ExcepcionAplicacion(CodigoError.CATEGORY_INACTIVE,
                        "No se puede activar una subcategoría dentro de una categoría inactiva.")
                        .con("categoriaId", subcategoria.getCategoria().getId())
                        .con("categoriaNombre", subcategoria.getCategoria().getNombre());
            }
            subcategoria.activar();
            return;
        }
        if (subcategoria.isActiva()) {
            Dependientes.exigirNinguno(
                    subcategorias.contarProductosActivos(subcategoria.getId()),
                    subcategorias.nombresDeProductosActivos(
                            subcategoria.getId(), PageRequest.of(0, Dependientes.MAXIMO_LISTADO)),
                    "la subcategoría '" + subcategoria.getNombre() + "'",
                    "productos");
        }
        subcategoria.desactivar();
    }

    /**
     * El nombre es único dentro de la categoría, no en toda la tienda. Se
     * comprueba aquí para dar un 409 claro, y la restricción compuesta de la
     * base sigue siendo el árbitro en caso de carrera.
     */
    private void exigirNombreLibre(Categoria categoria, String nombre, Long idQueSeEdita) {
        boolean ocupado = idQueSeEdita == null
                ? subcategorias.existsByCategoriaIdAndNombreIgnoreCase(categoria.getId(), nombre)
                : subcategorias.existsByCategoriaIdAndNombreIgnoreCaseAndIdNot(
                        categoria.getId(), nombre, idQueSeEdita);
        if (ocupado) {
            throw new ExcepcionAplicacion(CodigoError.DUPLICATE_NAME,
                    "Ya existe una subcategoría '" + nombre + "' en " + categoria.getNombre() + ".");
        }
    }

    /** find(id) se salta el @SQLRestriction: lo eliminado se descarta aquí. */
    private Subcategoria buscar(Long id) {
        return subcategorias.findById(id)
                .filter(subcategoria -> !subcategoria.estaEliminado())
                .orElseThrow(() -> noEncontrada(String.valueOf(id)));
    }

    private Categoria categoria(Long id) {
        return categorias.findById(id)
                .filter(categoria -> !categoria.estaEliminado())
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.CATEGORY_NOT_FOUND,
                        "No existe la categoría " + id + "."));
    }

    private static ExcepcionAplicacion noEncontrada(String referencia) {
        return new ExcepcionAplicacion(CodigoError.SUBCATEGORY_NOT_FOUND,
                "No existe la subcategoría '" + referencia + "'.");
    }

    private Archivo imagen(Long idArchivo) {
        return idArchivo == null ? null : archivos.referencia(idArchivo);
    }

    private static String textoODefecto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
