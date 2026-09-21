package com.retailstore.api.seguridad.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.retailstore.api.cliente.dominio.Cliente;
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
    @DisplayName("un token de tienda trae el cliente, su correo y su nombre")
    void emiteYValidaTienda() {
        String token = jwt.emitirAccesoTienda(cliente());

        var contenido = jwt.validarTienda(token);

        assertThat(contenido.idCliente()).isEqualTo(42L);
        assertThat(contenido.email()).isEqualTo("ana@ejemplo.pe");
        assertThat(contenido.nombre()).isEqualTo("Ana Torres");
    }

    @Test
    @DisplayName("un token de cliente NO abre el panel: WRONG_AUDIENCE")
    void tokenDeClienteNoAbreElPanel() {
        String token = jwt.emitirAccesoTienda(cliente());

        assertThatThrownBy(() -> jwt.validarPanel(token))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.WRONG_AUDIENCE);
    }

    @Test
    @DisplayName("y un token del panel NO abre la cuenta de la tienda: la separación corta en las dos direcciones")
    void tokenDePanelNoAbreLaCuenta() {
        String token = jwt.emitirAccesoPanel(administrador());

        assertThatThrownBy(() -> jwt.validarTienda(token))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.WRONG_AUDIENCE);
    }

    @Test
    @DisplayName("un token de tienda caducado se rechaza igual que uno de panel")
    void rechazaTokenDeTiendaCaducado() {
        String token = jwt.emitirAccesoTienda(cliente());
        ServicioJwt masTarde = new ServicioJwt(
                propiedades(SECRETO),
                Clock.fixed(AHORA.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> masTarde.validarTienda(token))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.UNAUTHENTICATED);
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
                Duration.ofMinutes(15), Duration.ofHours(12), Duration.ofDays(30), false);
    }

    private static Cliente cliente() {
        Cliente cliente = new Cliente("Ana@Ejemplo.PE", "Ana Torres", "999888777");
        fijarId(cliente, Cliente.class, 42L);
        return cliente;
    }

    private static Administrador administrador() {
        Administrador administrador = new Administrador(
                "admin", "{bcrypt}hash", "Admin", RolAdministrador.SUPERADMINISTRADOR);
        fijarId(administrador, Administrador.class, 7L);
        return administrador;
    }

    /**
     * El id lo pone la base al insertar y aquí no hay base. Se fija por
     * reflexión antes que añadir un constructor que solo usarían las pruebas:
     * un constructor así acaba usándose en producción y saltándose la
     * generación de claves.
     */
    private static void fijarId(Object entidad, Class<?> tipo, Long id) {
        try {
            Field campo = tipo.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(entidad, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
