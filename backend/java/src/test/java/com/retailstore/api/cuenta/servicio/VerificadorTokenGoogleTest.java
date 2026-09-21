package com.retailstore.api.cuenta.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verificación del ID token de Google.
 *
 * <p>Las claves las genera la propia prueba y las sirve un {@link ClavesGoogle}
 * de mentira. Es la razón de que esa interfaz exista: lo que hay que comprobar
 * es que se rechaza lo que no cuadra, y eso no se puede probar contra un
 * servicio que rota sus claves cuando quiere y al que no se le puede pedir un
 * token caducado.
 */
class VerificadorTokenGoogleTest {

    private static final Instant AHORA = Instant.parse("2026-06-15T12:00:00Z");
    private static final String CLIENT_ID = "123456789.apps.googleusercontent.com";
    private static final String KID = "clave-de-prueba";

    private final KeyPair par = generar();
    private final ClavesGoogle claves = kid -> KID.equals(kid) ? Optional.of(par.getPublic()) : Optional.empty();
    private final VerificadorTokenGoogle verificador = new VerificadorTokenGoogle(
            propiedades(CLIENT_ID), claves, Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("un token bien firmado devuelve sujeto, correo y nombre")
    void verificaTokenValido() {
        IdentidadGoogle identidad = verificador.verificar(token(Map.of(
                "sub", "10987654321",
                "email", "Ana@Ejemplo.PE",
                "email_verified", true,
                "name", "Ana Torres",
                "picture", "https://lh3.googleusercontent.com/foto")));

        assertThat(identidad.sujeto()).isEqualTo("10987654321");
        assertThat(identidad.emailVerificado()).isTrue();
        assertThat(identidad.nombre()).isEqualTo("Ana Torres");
    }

    @Test
    @DisplayName("el correo llega normalizado a minúsculas, como la identidad del cliente (RN-060)")
    void normalizaElCorreo_RN060() {
        IdentidadGoogle identidad = verificador.verificar(token(Map.of(
                "sub", "1", "email", "Ana@Ejemplo.PE", "email_verified", true)));

        assertThat(identidad.email()).isEqualTo("ana@ejemplo.pe");
    }

    @Test
    @DisplayName("un correo que Google no verificó se reporta como tal, no se asume verificado (RN-064)")
    void reportaCorreoNoVerificado_RN064() {
        IdentidadGoogle identidad = verificador.verificar(token(Map.of(
                "sub", "1", "email", "victima@ejemplo.pe", "email_verified", false)));

        assertThat(identidad.emailVerificado()).isFalse();
    }

    @Test
    @DisplayName("un token emitido para otra aplicación se rechaza")
    void rechazaOtraAudiencia() {
        String ajeno = Jwts.builder()
                .header().keyId(KID).and()
                .issuer("https://accounts.google.com")
                .audience().add("otra-aplicacion.apps.googleusercontent.com").and()
                .subject("1")
                .claim("email", "ana@ejemplo.pe")
                .claim("email_verified", true)
                .expiration(Date.from(AHORA.plus(Duration.ofMinutes(10))))
                .signWith(par.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verificador.verificar(ajeno))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("un token que no dice venir de Google se rechaza")
    void rechazaOtroEmisor() {
        String ajeno = Jwts.builder()
                .header().keyId(KID).and()
                .issuer("https://accounts.example.com")
                .audience().add(CLIENT_ID).and()
                .subject("1")
                .claim("email", "ana@ejemplo.pe")
                .claim("email_verified", true)
                .expiration(Date.from(AHORA.plus(Duration.ofMinutes(10))))
                .signWith(par.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verificador.verificar(ajeno))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("un token firmado con una clave que Google no publica se rechaza")
    void rechazaFirmaAjena() {
        KeyPair impostor = generar();
        String falsificado = Jwts.builder()
                .header().keyId(KID).and()
                .issuer("https://accounts.google.com")
                .audience().add(CLIENT_ID).and()
                .subject("1")
                .claim("email", "ana@ejemplo.pe")
                .claim("email_verified", true)
                .expiration(Date.from(AHORA.plus(Duration.ofMinutes(10))))
                .signWith(impostor.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verificador.verificar(falsificado))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("un kid que el proveedor no publica se rechaza sin probar otras claves")
    void rechazaKidDesconocido() {
        String otroKid = Jwts.builder()
                .header().keyId("kid-que-no-existe").and()
                .issuer("https://accounts.google.com")
                .audience().add(CLIENT_ID).and()
                .subject("1")
                .claim("email", "ana@ejemplo.pe")
                .claim("email_verified", true)
                .expiration(Date.from(AHORA.plus(Duration.ofMinutes(10))))
                .signWith(par.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verificador.verificar(otroKid))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("un token caducado se rechaza")
    void rechazaTokenCaducado() {
        String token = token(Map.of("sub", "1", "email", "ana@ejemplo.pe", "email_verified", true));
        VerificadorTokenGoogle masTarde = new VerificadorTokenGoogle(
                propiedades(CLIENT_ID), claves, Clock.fixed(AHORA.plus(Duration.ofMinutes(11)), ZoneOffset.UTC));

        assertThatThrownBy(() -> masTarde.verificar(token))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("sin GOOGLE_CLIENT_ID el endpoint no falla de forma opaca: 503 y el motivo")
    void exigeClientIdConfigurado() {
        VerificadorTokenGoogle sinConfigurar = new VerificadorTokenGoogle(
                propiedades(""), claves, Clock.fixed(AHORA, ZoneOffset.UTC));

        assertThatThrownBy(() -> sinConfigurar.verificar("da-igual"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .hasMessageContaining("GOOGLE_CLIENT_ID")
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.GOOGLE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("una cadena que no es un JWT se rechaza sin filtrar el motivo")
    void rechazaBasura() {
        assertThatThrownBy(() -> verificador.verificar("esto.no.es"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INVALID_PROVIDER_TOKEN);
    }

    // ----- apoyo -----

    private String token(Map<String, Object> claims) {
        var constructor = Jwts.builder()
                .header().keyId(KID).and()
                .issuer("https://accounts.google.com")
                .audience().add(CLIENT_ID).and()
                .expiration(Date.from(AHORA.plus(Duration.ofMinutes(10))));
        claims.forEach((clave, valor) -> {
            if ("sub".equals(clave)) {
                constructor.subject(String.valueOf(valor));
            } else {
                constructor.claim(clave, valor);
            }
        });
        return constructor.signWith(par.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static PropiedadesGoogle propiedades(String clientId) {
        return new PropiedadesGoogle(clientId, null, Duration.ofHours(1));
    }

    private static KeyPair generar() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            return generador.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
