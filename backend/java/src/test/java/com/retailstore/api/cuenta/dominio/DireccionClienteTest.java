package com.retailstore.api.cuenta.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import com.retailstore.api.cliente.dominio.Cliente;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El distrito llega nulo en estas pruebas a propósito: la columna es NOT NULL y
 * quien lo garantiza es la base, no la entidad. Lo que se comprueba aquí es lo
 * que la entidad sí decide.
 */
class DireccionClienteTest {

    @Test
    @DisplayName("media coordenada no ubica nada: o van las dos o no va ninguna")
    void mediaCoordenadaNoSeGuarda() {
        DireccionCliente direccion = nueva();

        direccion.ubicar(null, "Av. Los Olivos", "123", null, null, new BigDecimal("-12.0464"), null);

        assertThat(direccion.getLatitud()).isNull();
        assertThat(direccion.getLongitud()).isNull();
    }

    @Test
    @DisplayName("con las dos coordenadas, las dos se guardan")
    void parejaCompletaSeGuarda() {
        DireccionCliente direccion = nueva();

        direccion.ubicar(null, "Av. Los Olivos", "123", "Piso 4", "15074",
                new BigDecimal("-12.0464"), new BigDecimal("-77.0428"));

        assertThat(direccion.getLatitud()).isEqualByComparingTo("-12.0464");
        assertThat(direccion.getLongitud()).isEqualByComparingTo("-77.0428");
        assertThat(direccion.getReferencia()).isEqualTo("Piso 4");
    }

    @Test
    @DisplayName("una dirección nace sin ser la predeterminada; marcarla es una decisión aparte")
    void naceSinSerPredeterminada() {
        DireccionCliente direccion = nueva();

        assertThat(direccion.isPredeterminada()).isFalse();

        direccion.marcarPredeterminada();
        assertThat(direccion.isPredeterminada()).isTrue();

        direccion.quitarPredeterminada();
        assertThat(direccion.isPredeterminada()).isFalse();
    }

    @Test
    @DisplayName("dos direcciones recién creadas NO son la misma, aunque las dos tengan el id a nulo")
    void esMismaNoConfundeEntidadesNuevas() {
        DireccionCliente una = nueva();
        DireccionCliente otra = nueva();

        // Con Objects.equals sobre los ids, estas dos saldrían iguales; con
        // una.getId().equals(...) reventaría. Por eso existe esMisma().
        assertThat(una.esMisma(otra)).isFalse();
        assertThat(una.esMisma(null)).isFalse();
    }

    @Test
    @DisplayName("eliminar es lógico e idempotente: no reescribe quién fue_RN086")
    void eliminarEsIdempotente_RN086() {
        DireccionCliente direccion = nueva();
        var primera = java.time.Instant.parse("2026-06-15T12:00:00Z");

        direccion.eliminar(primera, "cliente:7");
        direccion.eliminar(primera.plusSeconds(3600), "cliente:9");

        assertThat(direccion.estaEliminado()).isTrue();
        assertThat(direccion.getEliminadoEn()).isEqualTo(primera);
        assertThat(direccion.getEliminadoPor()).isEqualTo("cliente:7");
    }

    private static DireccionCliente nueva() {
        return new DireccionCliente(
                new Cliente("ana@ejemplo.pe", "Ana", null), null,
                "Casa", "Ana Torres", "999888777", "Av. Los Olivos");
    }
}
