package com.retailstore.api.comun.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimitadorIntentosTest {

    private static final Instant INICIO = Instant.parse("2026-06-15T12:00:00Z");

    /** Reloj que se puede adelantar: sin esto habría que esperar 15 minutos reales. */
    private static final class RelojMovil extends Clock {
        private Instant ahora = INICIO;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }
    }

    @Test
    @DisplayName("admite hasta el máximo y bloquea el siguiente_RN068")
    void bloqueaAlSuperarElMaximo() {
        LimitadorIntentos limitador = new LimitadorIntentos(3, Duration.ofMinutes(15), new RelojMovil());

        assertThat(limitador.registrar("admin")).isZero();
        assertThat(limitador.registrar("admin")).isZero();
        assertThat(limitador.registrar("admin")).isZero();
        assertThat(limitador.registrar("admin")).isPositive();
    }

    @Test
    @DisplayName("dice cuántos segundos faltan para reintentar_RN068")
    void informaLaEspera() {
        RelojMovil reloj = new RelojMovil();
        LimitadorIntentos limitador = new LimitadorIntentos(1, Duration.ofMinutes(15), reloj);
        limitador.registrar("admin");

        reloj.avanzar(Duration.ofMinutes(5));

        assertThat(limitador.registrar("admin")).isEqualTo(Duration.ofMinutes(10).toSeconds());
    }

    @Test
    @DisplayName("la ventana se desliza: pasado el tiempo vuelve a admitir_RN068")
    void laVentanaSeDesliza() {
        RelojMovil reloj = new RelojMovil();
        LimitadorIntentos limitador = new LimitadorIntentos(2, Duration.ofMinutes(15), reloj);
        limitador.registrar("admin");
        limitador.registrar("admin");

        reloj.avanzar(Duration.ofMinutes(16));

        assertThat(limitador.registrar("admin")).isZero();
    }

    @Test
    @DisplayName("las claves no se estorban entre sí_RN068")
    void clavesIndependientes() {
        LimitadorIntentos limitador = new LimitadorIntentos(1, Duration.ofMinutes(15), new RelojMovil());
        limitador.registrar("admin");

        assertThat(limitador.registrar("otro")).isZero();
    }

    @Test
    @DisplayName("un acceso correcto limpia el contador_RN068")
    void olvidarLimpia() {
        LimitadorIntentos limitador = new LimitadorIntentos(1, Duration.ofMinutes(15), new RelojMovil());
        limitador.registrar("admin");

        limitador.olvidar("admin");

        assertThat(limitador.registrar("admin")).isZero();
    }
}
