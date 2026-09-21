package com.retailstore.api.orden.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class EstadoOrdenTest {

    @ParameterizedTest
    @CsvSource({
            "PENDIENTE,PAGADA",
            "PENDIENTE,CANCELADA",
            "PAGADA,ENVIADA",
            "PAGADA,CANCELADA",
            "ENVIADA,ENTREGADA",
    })
    @DisplayName("transiciones válidas del ciclo de vida_RN054")
    void transicionesValidas(EstadoOrden desde, EstadoOrden hasta) {
        assertThat(desde.puedeIrA(hasta)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            // Saltarse el pago o el envío deja el histórico sin explicación.
            "PENDIENTE,ENVIADA",
            "PENDIENTE,ENTREGADA",
            "PAGADA,ENTREGADA",
            // Cancelar algo que ya salió del almacén no devuelve nada.
            "ENVIADA,CANCELADA",
            // Los estados terminales lo son de verdad.
            "ENTREGADA,CANCELADA",
            "ENTREGADA,PAGADA",
            "CANCELADA,PENDIENTE",
            "CANCELADA,PAGADA",
    })
    @DisplayName("transiciones que no existen_RN054")
    void transicionesInvalidas(EstadoOrden desde, EstadoOrden hasta) {
        assertThat(desde.puedeIrA(hasta)).isFalse();
    }

    @Test
    @DisplayName("entregada y cancelada son terminales_RN054")
    void estadosTerminales() {
        assertThat(EstadoOrden.ENTREGADA.siguientes()).isEmpty();
        assertThat(EstadoOrden.CANCELADA.siguientes()).isEmpty();
    }

    @Test
    @DisplayName("solo cancelar devuelve el stock_RN055")
    void soloCancelarDevuelveStock() {
        assertThat(EstadoOrden.PENDIENTE.devuelveStock(EstadoOrden.CANCELADA)).isTrue();
        assertThat(EstadoOrden.PAGADA.devuelveStock(EstadoOrden.CANCELADA)).isTrue();
        assertThat(EstadoOrden.PENDIENTE.devuelveStock(EstadoOrden.PAGADA)).isFalse();
        assertThat(EstadoOrden.ENVIADA.devuelveStock(EstadoOrden.ENTREGADA)).isFalse();
    }

    @Test
    @DisplayName("una orden cancelada no cuenta como venta")
    void canceladaNoEsVenta() {
        assertThat(EstadoOrden.CANCELADA.cuentaComoVenta()).isFalse();
        assertThat(EstadoOrden.ENTREGADA.cuentaComoVenta()).isTrue();
    }

    @Test
    @DisplayName("no hay ciclos: desde PENDIENTE nunca se vuelve a PENDIENTE_RN054")
    void sinCiclos() {
        // Recorre el grafo entero. Un ciclo permitiría, por ejemplo, cancelar
        // y reactivar una orden indefinidamente devolviendo stock cada vez.
        Set<EstadoOrden> vistos = new HashSet<>();
        Deque<EstadoOrden> pendientes = new ArrayDeque<>(EstadoOrden.PENDIENTE.siguientes());

        while (!pendientes.isEmpty()) {
            EstadoOrden estado = pendientes.pop();
            assertThat(estado).isNotEqualTo(EstadoOrden.PENDIENTE);
            if (vistos.add(estado)) {
                pendientes.addAll(estado.siguientes());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(EstadoOrden.class)
    @DisplayName("todo estado tiene etiqueta para la interfaz")
    void todosTienenEtiqueta(EstadoOrden estado) {
        assertThat(estado.etiqueta()).isNotBlank();
    }
}
