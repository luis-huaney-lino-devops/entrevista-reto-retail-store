package com.retailstore.api.seguridad.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.seguridad.config.PropiedadesJwt;
import com.retailstore.api.seguridad.dominio.Administrador;
import com.retailstore.api.seguridad.dominio.RolAdministrador;
import io.jsonwebtoken.Jwts;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServicioJwtTest {

    private static final Instant AHORA = Instant.parse("2026-06-15T12:00:00Z");
    private static final String SECRETO = "un-secreto-de-pruebas-de-mas-de-32-bytes";

    private final ServicioJwt jwt = new ServicioJwt(propiedades(SECRETO), Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("un token recién emitido es válido y trae usuario y rol")
    void emiteYValida() {
        String token = jwt.emitirAccesoPanel(administrador());

        var contenido = jwt.validarPanel(token);

        assertThat(contenido.idAdministrador()).isEqualTo(7L);
        assertThat(contenido.usuario()).isEqualTo("admin");
        assertThat(contenido.rol()).isEqualTo(RolAdministrador.SUPERADMINISTRADOR);
    }

    @Test
    @DisplayName("un token de la tienda NO abre el panel: WRONG_AUDIENCE")
    void rechazaTokenDeOtraAudiencia() {
        String tokenTienda = Jwts.builder()
                .issuer("retail-store")
                .audience().add(ServicioJwt.AUDIENCIA_TIENDA).and()
                .subject("7")
                .claim("usuario", "cliente")
                .claim("rol", "ADMINISTRADOR")
                .expiration(Date.from(AHORA.plus(Duration.ofMinutes(15))))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwt.validarPanel(tokenTienda))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.WRONG_AUDIENCE);
    }

    @Test
    @DisplayName("un token firmado con otro secreto se rechaza")
    void rechazaFirmaAjena() {
        ServicioJwt otro = new ServicioJwt(
                propiedades("otro-secreto-distinto-de-mas-de-32-bytes"),
                Clock.fixed(AHORA, ZoneOffset.UTC));
        String token = otro.emitirAccesoPanel(administrador());

        assertThatThrownBy(() -> jwt.validarPanel(token))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("un token caducado se rechaza")
    void rechazaTokenCaducado() {
        String token = jwt.emitirAccesoPanel(administrador());
        ServicioJwt masTarde = new ServicioJwt(
                propiedades(SECRETO),
                Clock.fixed(AHORA.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> masTarde.validarPanel(token))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("una cadena que no es un JWT se rechaza sin filtrar el motivo")
    void rechazaBasura() {
        assertThatThrownBy(() -> jwt.validarPanel("esto.no.es"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .hasMessage("El token no es válido.");
    }

    @Test
    @DisplayName("un secreto de menos de 32 bytes impide arrancar")
    void exigeSecretoSuficiente() {
        assertThatThrownBy(() -> new ServicioJwt(propiedades("corto"), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    // ----- apoyo -----

    private static PropiedadesJwt propiedades(String secreto) {
        return new PropiedadesJwt(secreto, "retail-store",
                Duration.ofMinutes(15), Duration.ofHours(12), false);
    }

    private static Administrador administrador() {
        Administrador administrador = new Administrador(
                "admin", "{bcrypt}hash", "Admin", RolAdministrador.SUPERADMINISTRADOR);
        try {
            Field campo = Administrador.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(administrador, 7L);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return administrador;
    }
}
