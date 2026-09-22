package com.retailstore.api.orden.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.retailstore.api.carrito.dominio.Carrito;
import com.retailstore.api.carrito.dominio.EstadoCarrito;
import com.retailstore.api.carrito.repositorio.CarritoRepositorio;
import com.retailstore.api.carrito.servicio.CalculadoraCarrito;
import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.catalogo.dominio.ProductoDePrueba;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.cupon.dominio.Cupon;
import com.retailstore.api.cupon.dominio.TipoCupon;
import com.retailstore.api.notificacion.servicio.AvisosDeStock;
import com.retailstore.api.orden.dominio.Orden;
import com.retailstore.api.orden.dto.CrearOrdenPeticion;
import com.retailstore.api.orden.repositorio.OrdenRepositorio;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que decide el checkout.
 *
 * <p>Se prueba lo que no puede comprobar la base por sí sola: que los importes
 * salgan del servidor y no de la petición (RN-051), que la orden copie en vez
 * de referenciar (RN-052), que el stock se descuente y que un producto
 * despublicado o sin existencias corte la operación antes de crear nada
 * (RN-050), y que el cupón solo gaste un uso cuando de verdad se aplicó
 * (RN-042).
 */
class ServicioCheckoutTest {

    private static final UUID ID_CARRITO = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-09-15T10:00:00Z");

    private final CarritoRepositorio carritos = mock(CarritoRepositorio.class);
    private final OrdenRepositorio ordenes = mock(OrdenRepositorio.class);
    private final ClienteRepositorio clientes = mock(ClienteRepositorio.class);
    private final AvisosDeStock avisos = mock(AvisosDeStock.class);
    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);

    private final ServicioCheckout servicio = new ServicioCheckout(
            carritos, ordenes, clientes,
            new CalculadoraCarrito(reloj), new GeneradorNumeroOrden(reloj), avisos);

    // ------------------------------------------------------------ éxito

    @Test
    @DisplayName("el total lo calcula el servidor, no la petición_RN051")
    void elTotalLoCalculaElServidor() {
        Producto producto = ProductoDePrueba.publicado(1L, "34.90");
        prepararCarrito(carrito(producto, 2));

        Orden orden = confirmar();

        // 34.90 x 2, decidido aquí. La petición no lleva ningún importe:
        // CrearOrdenPeticion no tiene dónde ponerlo.
        assertThat(orden.getSubtotal()).isEqualByComparingTo("69.80");
        assertThat(orden.getDescuento()).isEqualByComparingTo("0.00");
        assertThat(orden.getTotal()).isEqualByComparingTo("69.80");
    }

    @Test
    @DisplayName("la línea copia nombre, SKU y precio del producto_RN052")
    void laLineaCopiaLosDatos() {
        Producto producto = ProductoDePrueba.publicado(1L, "34.90");
        prepararCarrito(carrito(producto, 3));

        Orden orden = confirmar();

        assertThat(orden.getItems()).hasSize(1);
        var linea = orden.getItems().get(0);
        assertThat(linea.getNombreProducto()).isEqualTo(producto.getNombre());
        assertThat(linea.getSku()).isEqualTo(producto.getSku());
        assertThat(linea.getPrecioUnitario()).isEqualByComparingTo("34.90");
        assertThat(linea.getTotalLinea()).isEqualByComparingTo("104.70");
    }

    @Test
    @DisplayName("confirmar descuenta el stock y cierra el carrito_RN050")
    void descuentaStockYCierraElCarrito() {
        Producto producto = ProductoDePrueba.publicado(1L, "10.00");   // nace con 100
        Carrito carrito = carrito(producto, 4);
        prepararCarrito(carrito);

        confirmar();

        assertThat(producto.getStock()).isEqualTo(96);
        assertThat(carrito.getEstado()).isEqualTo(EstadoCarrito.CONVERTIDO);
        verify(avisos).revisar(producto);
    }

    @Test
    @DisplayName("el número es legible y no correlativo_RN057")
    void elNumeroNoEsCorrelativo() {
        prepararCarrito(carrito(ProductoDePrueba.publicado(1L, "10.00"), 1));

        Orden orden = confirmar();

        assertThat(orden.getNumero()).matches("ORD-202609-[2-9A-HJ-NP-Z]{6}");
    }

    @Test
    @DisplayName("el correo de contacto se normaliza a minúsculas_RN056")
    void elCorreoSeNormaliza() {
        prepararCarrito(carrito(ProductoDePrueba.publicado(1L, "10.00"), 1));

        Orden orden = confirmarCon(new CrearOrdenPeticion(ID_CARRITO, "  Ana Torres  ",
                "  ANA@Ejemplo.PE ", "  ", " Av. Siempre Viva 742 "));

        assertThat(orden.getEmail()).isEqualTo("ana@ejemplo.pe");
        assertThat(orden.getNombreContacto()).isEqualTo("Ana Torres");
        // Un teléfono en blanco es no haberlo dado, no una cadena vacía.
        assertThat(orden.getTelefono()).isNull();
    }

    // ------------------------------------------------------------ cupones

    @Test
    @DisplayName("un cupón que aplica gasta un uso_RN042")
    void elCuponQueAplicaGastaUnUso() {
        Producto producto = ProductoDePrueba.publicado(1L, "100.00");
        Carrito carrito = carrito(producto, 1);
        carrito.aplicarCupon(cupon(BigDecimal.ZERO));
        prepararCarrito(carrito);

        Orden orden = confirmar();

        assertThat(carrito.getCupon().getUsosActuales()).isEqualTo(1);
        assertThat(orden.getDescuento()).isEqualByComparingTo("10.00");
        assertThat(orden.getTotal()).isEqualByComparingTo("90.00");
        assertThat(orden.getCodigoCupon()).isEqualTo("DIEZ");
    }

    @Test
    @DisplayName("un cupón vinculado que NO alcanza el mínimo no gasta uso_RN042")
    void elCuponQueNoAplicaNoGastaUso() {
        Producto producto = ProductoDePrueba.publicado(1L, "10.00");
        Carrito carrito = carrito(producto, 1);                      // subtotal 10
        carrito.aplicarCupon(cupon(new BigDecimal("50.00")));        // mínimo 50
        prepararCarrito(carrito);

        Orden orden = confirmar();

        // Sigue vinculado al carrito (RN-045) pero el comprador no se benefició,
        // así que consumir un uso sería cobrárselo por nada.
        assertThat(carrito.getCupon().getUsosActuales()).isZero();
        assertThat(orden.getDescuento()).isEqualByComparingTo("0.00");
        assertThat(orden.getCodigoCupon()).isNull();
    }

    // ------------------------------------------------------------ rechazos

    @Test
    @DisplayName("un producto despublicado corta el checkout_RN051")
    void productoDespublicadoCorta() {
        Producto producto = ProductoDePrueba.publicado(1L, "10.00");
        producto.desactivar();   // se despublica DESPUES de estar en el carrito
        prepararCarrito(carrito(producto, 1));

        assertThatThrownBy(this::confirmar)
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.PRODUCT_INACTIVE);

        verify(ordenes, never()).save(any());
    }

    @Test
    @DisplayName("sin stock suficiente no se crea la orden_RN051")
    void sinStockNoSeCreaLaOrden() {
        Producto producto = ProductoDePrueba.publicado(1L, "10.00");
        prepararCarrito(carrito(producto, 101));   // nace con 100

        assertThatThrownBy(this::confirmar)
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INSUFFICIENT_STOCK);

        verify(ordenes, never()).save(any());
    }

    @Test
    @DisplayName("un carrito ya convertido no se vuelve a confirmar_RN050")
    void carritoYaConvertido() {
        Carrito carrito = carrito(ProductoDePrueba.publicado(1L, "10.00"), 1);
        carrito.marcarConvertido();
        prepararCarrito(carrito);

        assertThatThrownBy(this::confirmar)
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.CART_ALREADY_CONVERTED);
    }

    @Test
    @DisplayName("un carrito vacío no se confirma_RN050")
    void carritoVacio() {
        prepararCarrito(new Carrito());

        assertThatThrownBy(this::confirmar)
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.CART_EMPTY);
    }

    // ------------------------------------------------------------ apoyo

    private Carrito carrito(Producto producto, int cantidad) {
        Carrito carrito = new Carrito();
        carrito.agregarOIncrementar(producto, cantidad);
        return carrito;
    }

    private Cupon cupon(BigDecimal subtotalMinimo) {
        return new Cupon("DIEZ", TipoCupon.PORCENTAJE, BigDecimal.TEN, subtotalMinimo,
                AHORA.minusSeconds(3600), AHORA.plusSeconds(3600), null);
    }

    private void prepararCarrito(Carrito carrito) {
        when(carritos.findConDetalleById(ID_CARRITO)).thenReturn(java.util.Optional.of(carrito));
        when(ordenes.existsByNumero(any())).thenReturn(false);
    }

    private Orden confirmar() {
        return confirmarCon(new CrearOrdenPeticion(ID_CARRITO, "Ana Torres",
                "ana@ejemplo.pe", "987654321", "Av. Siempre Viva 742"));
    }

    /** Devuelve la orden tal como se guardó, no el DTO: lo que se prueba es el hecho. */
    private Orden confirmarCon(CrearOrdenPeticion peticion) {
        servicio.crear(peticion, null);
        var capturada = org.mockito.ArgumentCaptor.forClass(Orden.class);
        verify(ordenes).save(capturada.capture());
        return capturada.getValue();
    }
}
