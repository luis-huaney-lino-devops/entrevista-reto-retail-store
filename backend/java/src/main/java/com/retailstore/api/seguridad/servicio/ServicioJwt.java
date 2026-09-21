package com.retailstore.api.seguridad.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.seguridad.config.PropiedadesJwt;
import com.retailstore.api.seguridad.dominio.Administrador;
import com.retailstore.api.seguridad.dominio.RolAdministrador;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emisión y validación de tokens de acceso.
 *
 * <p><strong>La audiencia es lo que separa los dos mundos.</strong> Un token de
 * cliente presentado al panel se rechaza aunque la firma sea válida, porque la
 * firma no es lo único que se comprueba. Sin esa comprobación, cualquier cuenta
 * de la tienda abriría el panel en cuanto compartieran secreto.
 */
@Service
public class ServicioJwt {

    public static final String AUDIENCIA_PANEL = "admin";
    public static final String AUDIENCIA_TIENDA = "storefront";

    private static final int BYTES_MINIMOS_CLAVE = 32;
    private static final String CLAIM_USUARIO = "usuario";
    private static final String CLAIM_ROL = "rol";

    private final SecretKey clave;
    private final PropiedadesJwt propiedades;
    private final Clock reloj;

    public ServicioJwt(PropiedadesJwt propiedades, Clock reloj) {
        byte[] bytes = propiedades.secreto() == null
                ? new byte[0]
                : propiedades.secreto().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < BYTES_MINIMOS_CLAVE) {
            // Fallar al arrancar y no al primer acceso: un secreto débil es un
            // error de despliegue, y el peor momento para descubrirlo es en
            // producción con tráfico.
            throw new IllegalStateException(
                    "app.jwt.secreto debe tener al menos " + BYTES_MINIMOS_CLAVE + " bytes; tiene " + bytes.length);
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public String emitirAccesoPanel(Administrador administrador) {
        Instant ahora = reloj.instant();
        return Jwts.builder()
                .issuer(propiedades.emisor())
                .audience().add(AUDIENCIA_PANEL).and()
                .subject(String.valueOf(administrador.getId()))
                .claim(CLAIM_USUARIO, administrador.getUsuario())
                .claim(CLAIM_ROL, administrador.getRol().name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(propiedades.vigenciaAcceso())))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida firma, emisor, vigencia y audiencia.
     *
     * @throws ExcepcionAplicacion {@code UNAUTHENTICATED} si el token no es
     *         válido; {@code WRONG_AUDIENCE} si lo es pero viene del otro mundo
     */
    public ContenidoToken validarPanel(String token) {
        Claims claims = analizar(token);
        Set<String> audiencia = claims.getAudience();
        if (audiencia == null || !audiencia.contains(AUDIENCIA_PANEL)) {
            // Un token de tienda llegando al panel no es un permiso que falta:
            // es alguien probando, o una aplicación mal configurada. Por eso
            // tiene código propio y se puede alertar.
            throw new ExcepcionAplicacion(CodigoError.WRONG_AUDIENCE,
                    "Este token no sirve para el panel de administración.");
        }
        return new ContenidoToken(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_USUARIO, String.class),
                RolAdministrador.valueOf(claims.get(CLAIM_ROL, String.class)));
    }

    public long segundosDeVigenciaAcceso() {
        return propiedades.vigenciaAcceso().toSeconds();
    }

    private Claims analizar(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(clave)
                    .requireIssuer(propiedades.emisor())
                    // El reloj de jjwt también sale del Clock inyectable: así una
                    // prueba puede adelantar el tiempo y comprobar la caducidad.
                    .clock(() -> Date.from(reloj.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            // Un solo código para firma inválida, token vencido, emisor
            // equivocado y cadena ilegible: distinguirlos solo ayuda a quien
            // está probando tokens.
            throw new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED, "El token no es válido.");
        }
    }

    /** Lo que el filtro necesita del token, ya validado. */
    public record ContenidoToken(Long idAdministrador, String usuario, RolAdministrador rol) {
    }
}
