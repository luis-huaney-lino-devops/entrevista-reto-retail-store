package com.retailstore.api.cuenta.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import com.retailstore.api.cliente.dominio.Cliente;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las dos condiciones que hacen inútil un token, por separado.
 *
 * <p>Importan por separado porque se confunden con facilidad: uno caducado que
 * nadie usó y uno usado que aún no caduca son igual de inválidos, y tratar solo
 * uno de los dos casos deja la puerta abierta al otro.
 */
class TokenClienteTest {

    private static final Instant AHORA = Instant.parse("2026-09-22T10:00:00Z");
    private final Cliente cliente = new Cliente("ana@ejemplo.pe", "Ana Torres", null);

    private TokenCliente nuevo(Duration vigencia) {
        return new TokenCliente(cliente, TipoTokenCliente.RECUPERAR_CONTRASENA, "hash", AHORA, vigencia);
    }

    @Test
    @DisplayName("recién emitido y sin usar, sirve_RN066")
    void recienEmitidoSirve() {
        assertThat(nuevo(Duration.ofMinutes(30)).esUtilizable(AHORA)).isTrue();
    }

    @Test
    @DisplayName("pasada la vigencia deja de servir aunque nadie lo haya usado_RN066")
    void caducadoNoSirve() {
        TokenCliente token = nuevo(Duration.ofMinutes(30));

        assertThat(token.esUtilizable(AHORA.plus(Duration.ofMinutes(29)))).isTrue();
        assertThat(token.esUtilizable(AHORA.plus(Duration.ofMinutes(31)))).isFalse();
    }

    @Test
    @DisplayName("justo en el instante de expiración ya no sirve_RN066")
    void elLimiteEsExclusivo() {
        TokenCliente token = nuevo(Duration.ofMinutes(30));

        // El borde importa: con `!isAfter` en vez de `isBefore`, un token
        // valdría un instante de más. No cambia nada en la práctica, pero es
        // el tipo de detalle que decide si la regla se cumple o «casi».
        assertThat(token.esUtilizable(token.getExpiraEn())).isFalse();
    }

    @Test
    @DisplayName("un solo uso: consumirlo lo invalida aunque no haya caducado_RN066")
    void unSoloUso() {
        TokenCliente token = nuevo(Duration.ofMinutes(30));

        token.consumir(AHORA.plusSeconds(60));

        assertThat(token.esUtilizable(AHORA.plusSeconds(61))).isFalse();
        assertThat(token.getUsadoEn()).isEqualTo(AHORA.plusSeconds(60));
    }
}
