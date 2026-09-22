package com.retailstore.api.opinion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.catalogo.dominio.ProductoDePrueba;
import com.retailstore.api.catalogo.repositorio.ProductoRepositorio;
import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.opinion.dominio.Opinion;
import com.retailstore.api.opinion.dto.ActualizarOpinionPeticion;
import com.retailstore.api.opinion.dto.CrearOpinionPeticion;
import com.retailstore.api.opinion.dto.OpinionRespuesta;
import com.retailstore.api.opinion.repositorio.OpinionRepositorio;
import com.retailstore.api.orden.repositorio.OrdenRepositorio;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que decide el servicio de opiniones.
 *
 * <p>Se prueban las tres cosas que no puede comprobar la base por sí sola: que
 * no se cuela una segunda opinión del mismo cliente (RN-090), que la insignia
 * la pone el servidor mirando las órdenes (RN-092) y que <strong>toda</strong>
 * escritura termina recalculando el promedio del producto (RN-093). Esta última
 * es la que más se rompería al añadir un camino nuevo, y por eso hay una
 * verificación por cada una de las tres escrituras.
 */
class ServicioOpinionTest {

    private static final Long ID_CLIENTE = 3L;
    private static final Long ID_PRODUCTO = 7L;

    private final OpinionRepositorio opiniones = mock(OpinionRepositorio.class);
    private final ProductoRepositorio productos = mock(ProductoRepositorio.class);
    private final ClienteRepositorio clientes = mock(ClienteRepositorio.class);
    private final OrdenRepositorio ordenes = mock(OrdenRepositorio.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);

    private final ServicioOpinion servicio =
            new ServicioOpinion(opiniones, productos, clientes, ordenes, reloj);

    private final Producto producto = ProductoDePrueba.conId(ID_PRODUCTO, "199.90");
    private final Cliente cliente = new Cliente("ana@ejemplo.pe", "Ana Torres", null);

    // ------------------------------------------------------------ crear

    @Test
    @DisplayName("quien ya opinó no crea una segunda: se le dice cuál es la suya_RN090")
    void crear_cuandoYaOpino_lanzaDuplicateReview_RN090() {
        dadoProductoVisible();
        dadoClienteActivo();
        when(opiniones.findByProductoIdAndClienteId(ID_PRODUCTO, ID_CLIENTE))
                .thenReturn(Optional.of(new Opinion(producto, cliente, (short) 4, "Ya está", "Escrita antes.")));

        assertThatThrownBy(() -> servicio.crear(ID_CLIENTE, peticionNueva(5)))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(ex -> ((ExcepcionAplicacion) ex).codigo())
                .isEqualTo(CodigoError.DUPLICATE_REVIEW);

        verify(opiniones, never()).save(any());
        verify(opiniones, never()).recalcularCalificacionDe(any());
    }

    @Test
    @DisplayName("con una orden ENTREGADA de ese producto, la opinión sale verificada_RN092")
    void crear_conOrdenEntregada_marcaCompraVerificada_RN092() {
        dadoProductoVisible();
        dadoClienteActivo();
        dadoSinOpinionPrevia();
        when(opiniones.save(any())).thenAnswer(llamada -> llamada.getArgument(0));
        when(ordenes.existeEntregadaConProducto(ID_CLIENTE, ID_PRODUCTO)).thenReturn(true);

        OpinionRespuesta respuesta = servicio.crear(ID_CLIENTE, peticionNueva(5));

        assertThat(respuesta.compraVerificada()).isTrue();
    }

    @Test
    @DisplayName("sin orden entregada se opina igual, pero sin insignia_RN091")
    void crear_sinCompraEntregada_seGuardaSinInsignia_RN091() {
        dadoProductoVisible();
        dadoClienteActivo();
        dadoSinOpinionPrevia();
        when(opiniones.save(any())).thenAnswer(llamada -> llamada.getArgument(0));
        // `existeEntregadaConProducto` no se estimula: devuelve false.

        OpinionRespuesta respuesta = servicio.crear(ID_CLIENTE, peticionNueva(4));

        assertThat(respuesta.compraVerificada()).isFalse();
        assertThat(respuesta.calificacion()).isEqualTo(4);
    }

    @Test
    @DisplayName("crear una opinión recalcula el promedio del producto_RN093")
    void crear_recalculaElPromedio_RN093() {
        dadoProductoVisible();
        dadoClienteActivo();
        dadoSinOpinionPrevia();
        when(opiniones.save(any())).thenAnswer(llamada -> llamada.getArgument(0));

        servicio.crear(ID_CLIENTE, peticionNueva(5));

        verify(opiniones).recalcularCalificacionDe(ID_PRODUCTO);
    }

    @Test
    @DisplayName("no se opina de un producto despublicado_RN091")
    void crear_sobreProductoNoVisible_lanzaProductNotFound_RN091() {
        dadoClienteActivo();
        when(productos.findByIdAndActivoTrue(ID_PRODUCTO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(ID_CLIENTE, peticionNueva(5)))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(ex -> ((ExcepcionAplicacion) ex).codigo())
                .isEqualTo(CodigoError.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("ni de uno cuya subcategoría está desactivada: para el comprador no existe_RN091")
    void crear_conSubcategoriaInvisible_lanzaProductNotFound_RN091() {
        dadoClienteActivo();
        producto.getSubcategoria().desactivar();
        dadoProductoVisible();

        assertThatThrownBy(() -> servicio.crear(ID_CLIENTE, peticionNueva(5)))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(ex -> ((ExcepcionAplicacion) ex).codigo())
                .isEqualTo(CodigoError.PRODUCT_NOT_FOUND);
    }

    // -------------------------------------------------------- actualizar

    @Test
    @DisplayName("editar vuelve a mirar las órdenes: la insignia se puede ganar después_RN092")
    void actualizar_recalculaLaInsignia_RN092() {
        Opinion mia = new Opinion(producto, cliente, (short) 2, "Antes", "Escrita antes de recibirlo.");
        when(opiniones.findByIdAndClienteId(55L, ID_CLIENTE)).thenReturn(Optional.of(mia));
        when(ordenes.existeEntregadaConProducto(ID_CLIENTE, ID_PRODUCTO)).thenReturn(true);

        OpinionRespuesta respuesta = servicio.actualizar(ID_CLIENTE, 55L,
                new ActualizarOpinionPeticion(5, "Después", "Ya lo tengo en las manos."));

        assertThat(respuesta.compraVerificada()).isTrue();
        assertThat(respuesta.calificacion()).isEqualTo(5);
        verify(opiniones).recalcularCalificacionDe(ID_PRODUCTO);
    }

    @Test
    @DisplayName("la opinión de otra persona es 404, nunca 403_RN094")
    void actualizar_deOtroCliente_lanzaReviewNotFound_RN094() {
        when(opiniones.findByIdAndClienteId(55L, ID_CLIENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(ID_CLIENTE, 55L,
                new ActualizarOpinionPeticion(1, "Título", "Cuerpo")))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(ex -> ((ExcepcionAplicacion) ex).codigo())
                .isEqualTo(CodigoError.REVIEW_NOT_FOUND);

        verify(opiniones, never()).recalcularCalificacionDe(any());
    }

    // ----------------------------------------------------------- eliminar

    @Test
    @DisplayName("retirar una opinión la marca, no la borra, y recalcula el promedio_RN094")
    void eliminar_marcaYRecalcula_RN094() {
        Opinion mia = new Opinion(producto, cliente, (short) 1, "Retirada", "Prefiero no dejarla.");
        when(opiniones.findByIdAndClienteId(55L, ID_CLIENTE)).thenReturn(Optional.of(mia));

        servicio.eliminar(ID_CLIENTE, 55L);

        assertThat(mia.estaEliminado()).isTrue();
        assertThat(mia.getEliminadoEn()).isEqualTo(Instant.parse("2026-03-01T10:00:00Z"));
        verify(opiniones).recalcularCalificacionDe(ID_PRODUCTO);
    }

    @Test
    @DisplayName("no se puede retirar la opinión de otra persona_RN094")
    void eliminar_deOtroCliente_lanzaReviewNotFound_RN094() {
        when(opiniones.findByIdAndClienteId(55L, ID_CLIENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(ID_CLIENTE, 55L))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(ex -> ((ExcepcionAplicacion) ex).codigo())
                .isEqualTo(CodigoError.REVIEW_NOT_FOUND);
    }

    // -------------------------------------------------------------- mía

    @Test
    @DisplayName("preguntar por la propia cuando no hay ninguna es 404, no un cuerpo vacío_RN090")
    void miOpinionDe_sinOpinion_lanzaReviewNotFound_RN090() {
        when(opiniones.findByProductoIdAndClienteId(ID_PRODUCTO, ID_CLIENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.miOpinionDe(ID_CLIENTE, ID_PRODUCTO))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(ex -> ((ExcepcionAplicacion) ex).codigo())
                .isEqualTo(CodigoError.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("el autor se publica abreviado: nombre y letra del apellido_RN091")
    void respuesta_abreviaElNombreDelAutor_RN091() {
        dadoProductoVisible();
        dadoClienteActivo();
        dadoSinOpinionPrevia();
        when(opiniones.save(any())).thenAnswer(llamada -> llamada.getArgument(0));

        OpinionRespuesta respuesta = servicio.crear(ID_CLIENTE, peticionNueva(5));

        assertThat(respuesta.autor()).isEqualTo("Ana T.");
    }

    // ------------------------------------------------------------ apoyo

    private void dadoProductoVisible() {
        when(productos.findByIdAndActivoTrue(ID_PRODUCTO)).thenReturn(Optional.of(producto));
    }

    private void dadoClienteActivo() {
        when(clientes.buscarActivo(ID_CLIENTE)).thenReturn(Optional.of(cliente));
    }

    private void dadoSinOpinionPrevia() {
        when(opiniones.findByProductoIdAndClienteId(ID_PRODUCTO, ID_CLIENTE)).thenReturn(Optional.empty());
    }

    private static CrearOpinionPeticion peticionNueva(int calificacion) {
        return new CrearOpinionPeticion(ID_PRODUCTO, calificacion, "Buen producto", "Lo uso a diario y cumple.");
    }
}
