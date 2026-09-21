package com.retailstore.api.catalogo.servicio;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.archivo.servicio.ServicioArchivo;
import com.retailstore.api.catalogo.dominio.Marca;
import com.retailstore.api.catalogo.dto.ActualizarMarcaPeticion;
import com.retailstore.api.catalogo.dto.CrearMarcaPeticion;
import com.retailstore.api.catalogo.dto.MarcaRespuesta;
import com.retailstore.api.catalogo.repositorio.MarcaRepositorio;
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
public class ServicioMarca {

    private final MarcaRepositorio marcas;
    private final ServicioArchivo archivos;
    private final Clock reloj;

    public ServicioMarca(MarcaRepositorio marcas, ServicioArchivo archivos, Clock reloj) {
        this.marcas = marcas;
        this.archivos = archivos;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------ panel

    @Transactional(readOnly = true)
    public RespuestaPagina<MarcaRespuesta> listar(String texto, int pagina, int tamanoPagina) {
        var paginacion = PageRequest.of(pagina - 1, tamanoPagina);
        var resultado = (texto == null || texto.isBlank())
                ? marcas.findAllByOrderByNombreAsc(paginacion)
                : marcas.findByNombreContainingIgnoreCaseOrderByNombreAsc(texto.trim(), paginacion);
        return RespuestaPagina.de(resultado, MarcaRespuesta::de);
    }

    @Transactional(readOnly = true)
    public MarcaRespuesta porId(Long id) {
        return MarcaRespuesta.de(buscar(id));
    }

    public MarcaRespuesta crear(CrearMarcaPeticion peticion) {
        String nombre = peticion.nombre().trim();
        Marca marca = new Marca(
                nombre,
                Slug.unico(nombre, marcas::existsBySlug),
                textoODefecto(peticion.descripcion()),
                logo(peticion.logoId()));
        return MarcaRespuesta.de(marcas.save(marca));
    }

    public MarcaRespuesta actualizar(Long id, ActualizarMarcaPeticion peticion) {
        Marca marca = buscar(id);
        marca.editar(peticion.nombre().trim(), textoODefecto(peticion.descripcion()), logo(peticion.logoId()));
        cambiarEstado(marca, peticion.activa());
        marcas.flush();
        return MarcaRespuesta.de(marca);
    }

    public MarcaRespuesta cambiarEstado(Long id, boolean activa) {
        Marca marca = buscar(id);
        cambiarEstado(marca, activa);
        marcas.flush();
        return MarcaRespuesta.de(marca);
    }

    /**
     * Eliminación lógica (RN-086): la fila se queda con la marca de quién y
     * cuándo. Antes se comprueba que no bloquee nada, igual que al desactivar.
     */
    public void eliminar(Long id) {
        Marca marca = buscar(id);
        Dependientes.exigirNinguno(
                marcas.contarProductosActivos(marca.getId()),
                marcas.nombresDeProductosActivos(marca.getId(), PageRequest.of(0, Dependientes.MAXIMO_LISTADO)),
                "eliminar la marca '" + marca.getNombre() + "'",
                "productos");
        marca.desactivar();
        marca.eliminar(reloj.instant(), UsuarioActual.nombre());
    }

    // ----------------------------------------------------------------- tienda

    @Transactional(readOnly = true)
    public List<MarcaRespuesta> visibles() {
        return marcas.findAllByActivaTrueOrderByNombreAsc().stream().map(MarcaRespuesta::de).toList();
    }

    @Transactional(readOnly = true)
    public MarcaRespuesta porSlug(String slug) {
        return MarcaRespuesta.de(marcas.findBySlug(slug)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.BRAND_NOT_FOUND,
                        "No existe la marca '" + slug + "'.")));
    }

    // ------------------------------------------------------------------ apoyo

    private void cambiarEstado(Marca marca, boolean activa) {
        if (activa) {
            marca.activar();
            return;
        }
        if (marca.isActiva()) {
            Dependientes.exigirNinguno(
                    marcas.contarProductosActivos(marca.getId()),
                    marcas.nombresDeProductosActivos(marca.getId(), PageRequest.of(0, Dependientes.MAXIMO_LISTADO)),
                    "la marca '" + marca.getNombre() + "'",
                    "productos");
        }
        marca.desactivar();
    }

    /**
     * find(id) va directo a la clave primaria y se salta el @SQLRestriction de
     * la entidad, así que lo eliminado hay que descartarlo aquí.
     */
    private Marca buscar(Long id) {
        return marcas.findById(id)
                .filter(marca -> !marca.estaEliminado())
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.BRAND_NOT_FOUND,
                        "No existe la marca " + id + "."));
    }

    private Archivo logo(Long idArchivo) {
        return idArchivo == null ? null : archivos.referencia(idArchivo);
    }

    private static String textoODefecto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
