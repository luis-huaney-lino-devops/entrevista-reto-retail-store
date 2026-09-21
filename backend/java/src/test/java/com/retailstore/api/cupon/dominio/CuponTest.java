package com.retailstore.api.cupon.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CuponTest {

    private static final Instant AHORA = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    @DisplayName("un cupón por porcentaje descuenta la parte proporcional_RN044")
    void descuentaPorcentaje() {
        Cupon cupon = porcentaje(10);

        assertThat(cupon.descuentoPara(new BigDecimal("299.80"))).isEqualByComparingTo("29.98");
    }

    @Test
    @DisplayName("el descuento nunca supera el subtotal_RN044")
    void elDescuentoNoSuperaElSubtotal() {
        Cupon cupon = montoFijo("50.00");

        assertThat(cupon.descuentoPara(new BigDecimal("30.00"))).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("fuera de vigencia no aplica_RN040")
    void fueraDeVigencia() {
        Cupon cupon = porcentaje(10);

        assertThat(cupon.estaVigente(AHORA.minus(Duration.ofDays(10)))).isFalse();
        assertThat(cupon.estaVigente(AHORA.plus(Duration.ofDays(10)))).isFalse();
        assertThat(cupon.estaVigente(AHORA)).isTrue();
    }

    @Test
    @DisplayName("el instante de fin es exclusivo_RN040")
    void elFinEsExclusivo() {
        Cupon cupon = porcentaje(10);

        assertThat(cupon.estaVigente(AHORA.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    @DisplayName("subtotal por debajo del mínimo lanza COUPON_MIN_NOT_MET_RN041")
    void minimoNoAlcanzado() {
        Cupon cupon = porcentaje(10);

        assertThatThrownBy(() -> cupon.exigirAplicable(new BigDecimal("49.99"), AHORA))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.COUPON_MIN_NOT_MET);
    }

    @Test
    @DisplayName("el mínimo es inclusivo_RN041")
    void elMinimoEsInclusivo() {
        Cupon cupon = porcentaje(10);

        assertThat(cupon.alcanzaMinimo(new BigDecimal("50.00"))).isTrue();
    }

    @Test
    @DisplayName("sin usos disponibles lanza COUPON_EXHAUSTED_RN042")
    void usosAgotados() {
        Cupon cupon = new Cupon("X", TipoCupon.PORCENTAJE, BigDecimal.TEN, BigDecimal.ZERO,
                AHORA.minus(Duration.ofDays(1)), AHORA.plus(Duration.ofDays(1)), 1);
        cupon.registrarUso();

        assertThat(cupon.quedanUsos()).isFalse();
        assertThatThrownBy(() -> cupon.exigirAplicable(new BigDecimal("100.00"), AHORA))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.COUPON_EXHAUSTED);
    }

    @Test
    @DisplayName("sin tope de usos nunca se agota_RN042")
    void sinTopeNoSeAgota() {
        Cupon cupon = porcentaje(10);

        for (int i = 0; i < 100; i++) {
            cupon.registrarUso();
        }

        assertThat(cupon.quedanUsos()).isTrue();
    }

    @Test
    @DisplayName("aplicaA no lanza: solo responde sí o no_RN045")
    void aplicaANoLanza() {
        Cupon cupon = porcentaje(10);

        assertThat(cupon.aplicaA(new BigDecimal("10.00"), AHORA)).isFalse();
        assertThat(cupon.aplicaA(new BigDecimal("100.00"), AHORA)).isTrue();
    }

    private static Cupon porcentaje(int valor) {
        return new Cupon("BIENVENIDA10", TipoCupon.PORCENTAJE, BigDecimal.valueOf(valor),
                new BigDecimal("50.00"), AHORA.minus(Duration.ofDays(1)), AHORA.plus(Duration.ofDays(1)), null);
    }

    private static Cupon montoFijo(String valor) {
        return new Cupon("ENVIO", TipoCupon.MONTO_FIJO, new BigDecimal(valor), BigDecimal.ZERO,
                AHORA.minus(Duration.ofDays(1)), AHORA.plus(Duration.ofDays(1)), null);
    }
}
