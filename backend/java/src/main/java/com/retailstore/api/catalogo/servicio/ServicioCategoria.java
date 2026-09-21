package com.retailstore.api.catalogo.servicio;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.archivo.servicio.ServicioArchivo;
import com.retailstore.api.catalogo.dominio.Categoria;
import com.retailstore.api.catalogo.dominio.Subcategoria;
import com.retailstore.api.catalogo.dto.ActualizarCategoriaPeticion;
import com.retailstore.api.catalogo.dto.CategoriaRespuesta;
import com.retailstore.api.catalogo.dto.CrearCategoriaPeticion;
import com.retailstore.api.catalogo.dto.SubcategoriaRespuesta;
import com.retailstore.api.catalogo.repositorio.CategoriaRepositorio;
import com.retailstore.api.catalogo.repositorio.SubcategoriaRepositorio;
import com.retailstore.api.comun.auditoria.UsuarioActual;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.comun.texto.Slug;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServicioCategoria {

    private final CategoriaRepositorio categorias;
    private final SubcategoriaRepositorio subcategorias;
    private final ServicioArchivo archivos;

    private final Clock reloj;

    public ServicioCategoria(CategoriaRepositorio categorias, SubcategoriaRepositorio subcategorias,
                             ServicioArchivo archivos, Clock reloj) {
        this.categorias = categorias;
        this.subcategorias = subcategorias;
        this.archivos = archivos;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------ panel

    @Transactional(readOnly = true)
    public RespuestaPagina<CategoriaRespuesta> listar(int pagina, int tamanoPagina) {
        return RespuestaPagina.de(
                categorias.findAllByOrderByOrdenAscNombreAsc(PageRequest.of(pagina - 1, tamanoPagina)),
                CategoriaRespuesta::de);
    }

    @Transactional(readOnly = true)
    public CategoriaRespuesta porId(Long id) {
        Categoria categoria = buscar(id);
        List<SubcategoriaRespuesta> hijas = subcategorias
                .findAllByCategoriaIdOrderByOrdenAscNombreAsc(id)
                .stream().map(SubcategoriaRespuesta::de).toList();
        return CategoriaRespuesta.de(categoria, hijas);
    }

    public CategoriaRespuesta crear(CrearCategoriaPeticion peticion) {
        String nombre = peticion.nombre().trim();
        Categoria categoria = new Categoria(
                nombre,
                Slug.unico(nombre, categorias::existsBySlug),
                textoODefecto(peticion.descripcion()),
                imagen(peticion.imagenId()),
                peticion.orden());
        return CategoriaRespuesta.de(categorias.save(categoria));
    }

    public CategoriaRespuesta actualizar(Long id, ActualizarCategoriaPeticion peticion) {
        Categoria categoria = buscar(id);
        categoria.editar(peticion.nombre().trim(), textoODefecto(peticion.descripcion()),
                imagen(peticion.imagenId()), peticion.orden());
        cambiarEstado(categoria, peticion.activa());
        categorias.flush();
        return CategoriaRespuesta.de(categoria);
    }

    public CategoriaRespuesta cambiarEstado(Long id, boolean activa) {
        Categoria categoria = buscar(id);
        cambiarEstado(categoria, activa);
        categorias.flush();
        return CategoriaRespuesta.de(categoria);
    }

    /** Eliminación lógica (RN-086). No se puede si tiene subcategorías activas. */
    public void eliminar(Long id) {
        Categoria categoria = buscar(id);
        Dependientes.exigirNinguno(
                categorias.contarSubcategoriasActivas(categoria.getId()),
                categorias.nombresDeSubcategoriasActivas(
                        categoria.getId(), PageRequest.of(0, Dependientes.MAXIMO_LISTADO)),
                "eliminar la categoría '" + categoria.getNombre() + "'",
                "subcategorías");
        categoria.desactivar();
        categoria.eliminar(reloj.instant(), UsuarioActual.nombre());
    }

    // ----------------------------------------------------------------- tienda

    /**
     * El menú completo en <strong>dos</strong> consultas, no en una por
     * categoría: se traen las subcategorías visibles y se agrupan en memoria.
     * Con seis categorías la diferencia no se nota; con sesenta, sí.
     */
    @Transactional(readOnly = true)
    public List<CategoriaRespuesta> menu() {
        List<Categoria> activas = categorias.findAllByActivaTrueOrderByOrdenAscNombreAsc();

        Map<Long, List<SubcategoriaRespuesta>> porCategoria = new LinkedHashMap<>();
        for (Subcategoria subcategoria : subcategorias.menuVisible()) {
            porCategoria.computeIfAbsent(subcategoria.getCategoria().getId(), clave -> new ArrayList<>())
                    .add(SubcategoriaRespuesta.de(subcategoria));
        }

        return activas.stream()
                .map(categoria -> CategoriaRespuesta.de(
                        categoria, porCategoria.getOrDefault(categoria.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaRespuesta porSlug(String slug) {
        Categoria categoria = categorias.findBySlug(slug)
                .orElseThrow(() -> noEncontrada(slug));
        List<SubcategoriaRespuesta> hijas = subcategorias
                .findAllByCategoriaIdOrderByOrdenAscNombreAsc(categoria.getId())
                .stream().filter(Subcategoria::isActiva).map(SubcategoriaRespuesta::de).toList();
        return CategoriaRespuesta.de(categoria, hijas);
    }

    // ------------------------------------------------------------------ apoyo

    private void cambiarEstado(Categoria categoria, boolean activa) {
        if (activa) {
            categoria.activar();
            return;
        }
        if (categoria.isActiva()) {
            // RN-012: desactivar NO cae en cascada. Un clic que despublica
            // doscientos productos es cómodo y peligroso: nadie se entera hasta
            // que caen las ventas.
            Dependientes.exigirNinguno(
                    categorias.contarSubcategoriasActivas(categoria.getId()),
                    categorias.nombresDeSubcategoriasActivas(
                            categoria.getId(), PageRequest.of(0, Dependientes.MAXIMO_LISTADO)),
                    "la categoría '" + categoria.getNombre() + "'",
                    "subcategorías");
        }
        categoria.desactivar();
    }

    /** find(id) se salta el @SQLRestriction: lo eliminado se descarta aquí. */
    private Categoria buscar(Long id) {
        return categorias.findById(id)
                .filter(categoria -> !categoria.estaEliminado())
                .orElseThrow(() -> noEncontrada(String.valueOf(id)));
    }

    private static ExcepcionAplicacion noEncontrada(String referencia) {
        return new ExcepcionAplicacion(CodigoError.CATEGORY_NOT_FOUND,
                "No existe la categoría '" + referencia + "'.");
    }

    private Archivo imagen(Long idArchivo) {
        return idArchivo == null ? null : archivos.referencia(idArchivo);
    }

    private static String textoODefecto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
