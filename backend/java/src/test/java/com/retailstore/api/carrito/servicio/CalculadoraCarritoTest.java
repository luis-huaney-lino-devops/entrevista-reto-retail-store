package com.retailstore.api.carrito.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import com.retailstore.api.carrito.dominio.Carrito;
import com.retailstore.api.catalogo.dominio.ProductoDePrueba;
import com.retailstore.api.cupon.dominio.Cupon;
import com.retailstore.api.cupon.dominio.TipoCupon;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalculadoraCarritoTest {

    private static final Instant AHORA = Instant.parse("2026-06-15T12:00:00Z");
    private final CalculadoraCarrito calculadora =
            new CalculadoraCarrito(Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("carrito vacío: todo a cero")
    void carritoVacio() {
        PrecioCarrito precio = calculadora.calcular(new Carrito());

        assertThat(precio.subtotal()).isEqualByComparingTo("0.00");
        assertThat(precio.total()).isEqualByComparingTo("0.00");
        assertThat(precio.cuponActivo()).isFalse();
    }

    @Test
    @DisplayName("el subtotal suma las líneas_RN051")
    void sumaLasLineas() {
        Carrito carrito = new Carrito();
        carrito.agregarOIncrementar(ProductoDePrueba.conId(1L, "149.90"), 2);
        carrito.agregarOIncrementar(ProductoDePrueba.conId(2L, "59.90"), 1);

        assertThat(calculadora.calcular(carrito).subtotal()).isEqualByComparingTo("359.70");
    }

    @Test
    @DisplayName("aplica el cupón vigente que alcanza el mínimo")
    void aplicaCupon() {
        Carrito carrito = conSubtotal("299.80");
        carrito.aplicarCupon(cupon(new BigDecimal("50.00")));

        PrecioCarrito precio = calculadora.calcular(carrito);

        assertThat(precio.descuento()).isEqualByComparingTo("29.98");
        assertThat(precio.total()).isEqualByComparingTo("269.82");
        assertThat(precio.cuponActivo()).isTrue();
        assertThat(precio.motivoCuponInactivo()).isNull();
    }

    @Test
    @DisplayName("un cupón que deja de alcanzar el mínimo no descuenta pero sigue aplicado_RN045")
    void cuponQueDejaDeAplicar() {
        Carrito carrito = conSubtotal("40.00");
        carrito.aplicarCupon(cupon(new BigDecimal("50.00")));

        PrecioCarrito precio = calculadora.calcular(carrito);

        assertThat(precio.descuento()).isEqualByComparingTo("0.00");
        assertThat(precio.total()).isEqualByComparingTo("40.00");
        assertThat(precio.cuponActivo()).isFalse();
        assertThat(precio.motivoCuponInactivo()).contains("compra mínima");
        assertThat(carrito.getCupon()).isNotNull();
    }

    @Test
    @DisplayName("un cupón caducado se ignora y lo explica_RN040")
    void cuponCaducado() {
        Carrito carrito = conSubtotal("100.00");
        carrito.aplicarCupon(new Cupon("VIEJO", TipoCupon.PORCENTAJE, BigDecimal.TEN, BigDecimal.ZERO,
                AHORA.minus(Duration.ofDays(30)), AHORA.minus(Duration.ofDays(1)), null));

        PrecioCarrito precio = calculadora.calcular(carrito);

        assertThat(precio.cuponActivo()).isFalse();
        assertThat(precio.motivoCuponInactivo()).contains("vigente");
    }

    private static Carrito conSubtotal(String subtotal) {
        Carrito carrito = new Carrito();
        carrito.agregarOIncrementar(ProductoDePrueba.conId(1L, subtotal), 1);
        return carrito;
    }

    private static Cupon cupon(BigDecimal minimo) {
        return new Cupon("BIENVENIDA10", TipoCupon.PORCENTAJE, BigDecimal.TEN, minimo,
                AHORA.minus(Duration.ofDays(1)), AHORA.plus(Duration.ofDays(1)), null);
    }
}
