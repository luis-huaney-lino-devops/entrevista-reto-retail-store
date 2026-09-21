package com.retailstore.api.cuenta.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.ProtectedHeader;
import java.security.Key;
import java.time.Clock;
import java.util.Date;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Comprueba un ID token de Google antes de creerse una sola palabra de lo que
 * dice.
 *
 * <p>Se verifican cinco cosas y ninguna sobra:
 *
 * <ol>
 *   <li><strong>La firma</strong>, contra las claves públicas del proveedor. Sin
 *       esto, cualquiera fabrica un token que diga ser quien quiera.</li>
 *   <li><strong>El emisor.</strong> Google firma más cosas que ID tokens.</li>
 *   <li><strong>La audiencia</strong> contra nuestro client id. Un token
 *       legítimo emitido para otra aplicación es un token robado aquí: quien
 *       controle esa otra aplicación podría reenviárnoslo.</li>
 *   <li><strong>La vigencia</strong>, que la hace jjwt con nuestro reloj.</li>
 *   <li><strong>{@code email_verified}</strong> (RN-064). Es lo único que
 *       impide que alguien cree una cuenta de Google declarando el correo de la
 *       víctima y se apropie de su cuenta en la tienda.</li>
 * </ol>
 *
 * <p>No hay flujo de {@code state} ni de {@code nonce} porque no hay redirección
 * que proteger: Google Identity Services entrega el ID token al JavaScript de
 * la tienda y este lo manda aquí. Lo que en el flujo de redirección garantiza
 * el {@code state} -que la respuesta pertenece a esta petición- aquí lo
 * garantiza que el token venga en el cuerpo de la llamada del propio usuario.
 */
@Component
public class VerificadorTokenGoogle {

    /** Google usa las dos formas en el claim {@code iss}, y las dos son válidas. */
    private static final Set<String> EMISORES = Set.of("accounts.google.com", "https://accounts.google.com");

    private final PropiedadesGoogle propiedades;
    private final ClavesGoogle claves;
    private final Clock reloj;

    public VerificadorTokenGoogle(PropiedadesGoogle propiedades, ClavesGoogle claves, Clock reloj) {
        this.propiedades = propiedades;
        this.claves = claves;
        this.reloj = reloj;
    }

    public boolean estaConfigurado() {
        return propiedades.estaConfigurado();
    }

    /**
     * @throws ExcepcionAplicacion {@code GOOGLE_NOT_CONFIGURED} si falta el
     *         client id; {@code INVALID_PROVIDER_TOKEN} si el token no supera
     *         cualquiera de las comprobaciones
     */
    public IdentidadGoogle verificar(String credencial) {
        if (!estaConfigurado()) {
            // 503 y no 500: el servicio existe y funcionará en cuanto se
            // configure GOOGLE_CLIENT_ID. Decirlo así evita que alguien
            // depure durante una hora un fallo que es una variable de entorno.
            throw new ExcepcionAplicacion(CodigoError.GOOGLE_NOT_CONFIGURED,
                    "El acceso con Google no está configurado en este entorno: falta GOOGLE_CLIENT_ID.");
        }

        Claims claims = analizar(credencial);

        if (!EMISORES.contains(String.valueOf(claims.getIssuer()))) {
            throw invalido();
        }
        Set<String> audiencia = claims.getAudience();
        if (audiencia == null || !audiencia.contains(propiedades.clientId())) {
            throw invalido();
        }

        String sujeto = claims.getSubject();
        String email = claims.get("email", String.class);
        if (sujeto == null || sujeto.isBlank() || email == null || email.isBlank()) {
            throw invalido();
        }

        return new IdentidadGoogle(
                sujeto,
                email.trim().toLowerCase(java.util.Locale.ROOT),
                esVerdadero(claims.get("email_verified")),
                claims.get("name", String.class),
                claims.get("picture", String.class));
    }

    private Claims analizar(String credencial) {
        try {
            return Jwts.parser()
                    .keyLocator(localizador())
                    .clock(() -> Date.from(reloj.instant()))
                    .build()
                    .parseSignedClaims(credencial)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw invalido();
        }
    }

    /**
     * La clave se elige por el {@code kid} de la cabecera. Probar todas las
     * publicadas hasta que una valide funcionaría igual y perdería la
     * comprobación de que el token dice con cuál se firmó.
     */
    private Locator<Key> localizador() {
        return cabecera -> {
            String kid = cabecera instanceof ProtectedHeader protegida ? protegida.getKeyId() : null;
            return claves.porIdentificador(kid).orElse(null);
        };
    }

    /** Google manda un booleano; algún proveedor manda la cadena. Se aceptan los dos. */
    private static boolean esVerdadero(Object valor) {
        return Boolean.TRUE.equals(valor) || "true".equalsIgnoreCase(String.valueOf(valor));
    }

    /**
     * Un solo error para firma inválida, emisor ajeno, audiencia ajena y token
     * ilegible: separarlos solo ayuda a quien está probando tokens a saber por
     * dónde va.
     */
    private static ExcepcionAplicacion invalido() {
        return new ExcepcionAplicacion(CodigoError.INVALID_PROVIDER_TOKEN,
                "No pudimos verificar tu cuenta de Google. Inténtalo de nuevo.");
    }
}
