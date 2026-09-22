package com.retailstore.api.opinion.servicio;

import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.catalogo.repositorio.ProductoRepositorio;
import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.auditoria.UsuarioActual;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.opinion.dominio.Opinion;
import com.retailstore.api.opinion.dto.ActualizarOpinionPeticion;
import com.retailstore.api.opinion.dto.BusquedaOpinionPeticion;
import com.retailstore.api.opinion.dto.CrearOpinionPeticion;
import com.retailstore.api.opinion.dto.OpinionRespuesta;
import com.retailstore.api.opinion.repositorio.OpinionRepositorio;
import com.retailstore.api.orden.repositorio.OrdenRepositorio;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las opiniones de producto (RN-090 … RN-094).
 *
 * <p>Tres cosas gobiernan todo lo de aquí:
 *
 * <ol>
 *   <li><strong>Una por cliente y producto</strong> (RN-090). Se comprueba
 *       antes para poder dar un error legible -dice cuál es la que ya existe,
 *       para ir a editarla-, pero quien lo garantiza es el índice único
 *       parcial: dos pestañas enviando el formulario a la vez pasan las dos la
 *       comprobación.</li>
 *   <li><strong>La insignia la calcula el servidor</strong> (RN-092), al crear
 *       y al editar, preguntando por las órdenes entregadas de ese cliente con
 *       ese producto.</li>
 *   <li><strong>El promedio se deriva</strong> (RN-093). Toda escritura termina
 *       llamando a {@code recalcularCalificacionDe}, y no hay otra forma en
 *       toda la aplicación de tocar esas dos columnas.</li>
 * </ol>
 *
 * <p>Sobre el orden dentro de cada método: se vuelca, se arma la respuesta y
 * solo entonces se recalcula. La auditoría -{@code actualizadoEn},
 * {@code version}- la escribe Hibernate al volcar, así que armar el DTO antes
 * devolvería los valores anteriores; y el recálculo es una sentencia masiva que
 * deja desfasada la copia en memoria del producto. No se reescribe -una
 * actualización masiva no ensucia la entidad, así que Hibernate no la vuelca-
 * pero tampoco se vuelve a leer en la misma transacción.
 */
@Service
@Transactional
public class ServicioOpinion {

    private final OpinionRepositorio opiniones;
    private final ProductoRepositorio productos;
    private final ClienteRepositorio clientes;
    private final OrdenRepositorio ordenes;
    private final Clock reloj;

    public ServicioOpinion(OpinionRepositorio opiniones, ProductoRepositorio productos,
                           ClienteRepositorio clientes, OrdenRepositorio ordenes, Clock reloj) {
        this.opiniones = opiniones;
        this.productos = productos;
        this.clientes = clientes;
        this.ordenes = ordenes;
        this.reloj = reloj;
    }

    // ============================================================ tienda

    /** Listado público de las opiniones de un producto visible. */
    @Transactional(readOnly = true)
    public RespuestaPagina<OpinionRespuesta> listarDeProducto(String slug, BusquedaOpinionPeticion peticion) {
        Producto producto = exigirVisiblePorSlug(slug);
        Page<Opinion> pagina = opiniones.findByProductoId(producto.getId(), paginacion(peticion));
        return RespuestaPagina.de(pagina, OpinionRespuesta::de);
    }

    // ============================================================ cuenta

    /**
     * La opinión que este cliente escribió sobre este producto.
     *
     * <p>Es lo que permite que la ficha ofrezca «editar la tuya» en vez de un
     * formulario en blanco que acabaría en un {@code 409}.
     *
     * <p>Devuelve {@code 404} cuando no hay ninguna, y no un cuerpo vacío: el
     * recurso «mi opinión sobre el producto 7» todavía no existe, y un
     * {@code 200} con nulo obligaría a distinguir dos formas de «nada».
     */
    @Transactional(readOnly = true)
    public OpinionRespuesta miOpinionDe(Long idCliente, Long idProducto) {
        return opiniones.findByProductoIdAndClienteId(idProducto, idCliente)
                .map(OpinionRespuesta::de)
                .orElseThrow(ServicioOpinion::noEncontrada);
    }

    public OpinionRespuesta crear(Long idCliente, CrearOpinionPeticion peticion) {
        Cliente cliente = exigirCliente(idCliente);
        Producto producto = exigirVisiblePorId(peticion.productoId());

        // RN-090. La comprobación previa existe por el mensaje; la garantía es
        // uq_opinion_cliente_producto, que además es parcial: quien retiró la
        // suya vuelve a tener el sitio libre.
        opiniones.findByProductoIdAndClienteId(producto.getId(), idCliente).ifPresent(ya -> {
            throw new ExcepcionAplicacion(CodigoError.DUPLICATE_REVIEW,
                    "Ya opinaste sobre este producto. Puedes editar lo que escribiste.")
                    .con("opinionId", ya.getId())
                    .con("productoId", producto.getId());
        });

        Opinion opinion = new Opinion(producto, cliente,
                peticion.calificacion().shortValue(), peticion.titulo(), peticion.cuerpo());
        opinion.marcarCompraVerificada(verificada(idCliente, producto.getId()));

        // Entidad nueva: save(). Un flush() a secas no escribiría nada porque
        // nadie la ha metido en la sesión, y la respuesta saldría con id nulo.
        Opinion guardada = opiniones.save(opinion);
        opiniones.flush();

        OpinionRespuesta respuesta = OpinionRespuesta.de(guardada);
        opiniones.recalcularCalificacionDe(producto.getId());
        return respuesta;
    }

    public OpinionRespuesta actualizar(Long idCliente, Long idOpinion, ActualizarOpinionPeticion peticion) {
        Opinion opinion = exigirPropia(idCliente, idOpinion);
        Long idProducto = opinion.getProducto().getId();

        opinion.escribir(peticion.calificacion().shortValue(), peticion.titulo(), peticion.cuerpo());
        // Se vuelve a calcular: quien opinó antes de recibir el pedido y vuelve
        // a pasar por su texto después se gana la insignia (RN-092).
        opinion.marcarCompraVerificada(verificada(idCliente, idProducto));

        // Ya gestionada: flush(), nunca saveAndFlush(). saveAndFlush() sobre
        // una entidad gestionada hace merge, y devolvería una copia.
        opiniones.flush();

        OpinionRespuesta respuesta = OpinionRespuesta.de(opinion);
        opiniones.recalcularCalificacionDe(idProducto);
        return respuesta;
    }

    /**
     * Eliminación lógica (RN-094): la fila se queda, con quién la retiró y
     * cuándo, y el promedio se recalcula sin ella.
     *
     * <p>El sitio queda libre: el índice único es parcial, así que quien retiró
     * la suya puede volver a opinar.
     */
    public void eliminar(Long idCliente, Long idOpinion) {
        Opinion opinion = exigirPropia(idCliente, idOpinion);
        Long idProducto = opinion.getProducto().getId();

        opinion.eliminar(reloj.instant(), UsuarioActual.nombre());
        opiniones.flush();
        opiniones.recalcularCalificacionDe(idProducto);
    }

    // ============================================================= apoyo

    private boolean verificada(Long idCliente, Long idProducto) {
        return ordenes.existeEntregadaConProducto(idCliente, idProducto);
    }

    /**
     * La opinión de otra persona es un {@code 404}, no un {@code 403}.
     *
     * <p>Un {@code 403} confirmaría que esa opinión existe, y con
     * identificadores correlativos eso es un contador de opiniones ajenas. La
     * consulta va acotada por cliente para que no haya forma de equivocarse.
     */
    private Opinion exigirPropia(Long idCliente, Long idOpinion) {
        return opiniones.findByIdAndClienteId(idOpinion, idCliente)
                .orElseThrow(ServicioOpinion::noEncontrada);
    }

    private Cliente exigirCliente(Long idCliente) {
        return clientes.buscarActivo(idCliente)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED,
                        "Tu sesión ya no es válida. Vuelve a entrar."));
    }

    /**
     * RN-091: no se opina de lo que no se puede ver.
     *
     * <p>La visibilidad se le pregunta a la entidad -{@code esVisible()}- en
     * lugar de repetir aquí qué significa: es la misma regla que aplica el
     * catálogo, y tenerla escrita dos veces es tenerla escrita mal una de las
     * dos.
     */
    private Producto exigirVisiblePorSlug(String slug) {
        Producto producto = productos.findBySlugAndActivoTrue(slug)
                .orElseThrow(() -> noExisteProducto(slug));
        if (!producto.getSubcategoria().esVisible()) {
            throw noExisteProducto(slug);
        }
        return producto;
    }

    private Producto exigirVisiblePorId(Long idProducto) {
        Producto producto = productos.findByIdAndActivoTrue(idProducto)
                .orElseThrow(() -> noExisteProducto(String.valueOf(idProducto)));
        if (!producto.getSubcategoria().esVisible()) {
            throw noExisteProducto(String.valueOf(idProducto));
        }
        return producto;
    }

    private static Pageable paginacion(BusquedaOpinionPeticion peticion) {
        return PageRequest.of(
                peticion.paginaEfectiva() - 1,
                peticion.tamanoEfectivo(),
                OrdenOpinion.desde(peticion.orden()).orden());
    }

    private static ExcepcionAplicacion noEncontrada() {
        return new ExcepcionAplicacion(CodigoError.REVIEW_NOT_FOUND, "No encontramos esa opinión.");
    }

    private static ExcepcionAplicacion noExisteProducto(String referencia) {
        return new ExcepcionAplicacion(CodigoError.PRODUCT_NOT_FOUND,
                "No existe el producto " + referencia + ".");
    }
}
