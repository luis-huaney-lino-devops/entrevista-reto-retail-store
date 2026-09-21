package com.retailstore.api.seguridad.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.limite.LimitadorIntentos;
import com.retailstore.api.seguridad.config.PropiedadesJwt;
import com.retailstore.api.seguridad.dominio.Administrador;
import com.retailstore.api.seguridad.dominio.TokenRefrescoAdmin;
import com.retailstore.api.seguridad.repositorio.AdministradorRepositorio;
import com.retailstore.api.seguridad.repositorio.TokenRefrescoAdminRepositorio;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceso al panel: entrar, refrescar y salir.
 *
 * <p>Tres decisiones que no se ven en la firma de los métodos:
 *
 * <ol>
 *   <li><strong>Los fallos son indistinguibles</strong> (RN-067). Usuario
 *       inexistente, contraseña equivocada y cuenta desactivada devuelven el
 *       mismo código y tardan lo mismo. Distinguirlos convierte el endpoint en
 *       un comprobador de qué usuarios existen.</li>
 *   <li><strong>El refresco rota</strong>. Cada uso invalida el anterior. Si uno
 *       ya usado reaparece, alguien lo copió y cae la familia entera.</li>
 *   <li><strong>Se guarda el hash del refresco</strong>, nunca el valor. Quien
 *       lea la tabla no puede fabricar una sesión.</li>
 * </ol>
 */
@Service
@Transactional
public class ServicioAccesoPanel {

    private static final Logger log = LoggerFactory.getLogger(ServicioAccesoPanel.class);

    /**
     * Hash real de una contraseña que nadie usa. Sirve para gastar el mismo
     * tiempo de CPU cuando el usuario no existe: sin esto, un acceso fallido es
     * ~100 ms más rápido para un usuario inexistente que para uno real, y esa
     * diferencia es medible desde fuera.
     */
    private static final String HASH_SENUELO =
            "{bcrypt}$2a$12$n.J8FZE2PDoPAo159JUo5ejIw8Sa4MeamcjO9jfKAw8iZR6nQXEn.";

    private static final int BYTES_TOKEN_REFRESCO = 32;
    private static final int MAXIMO_POR_USUARIO = 5;
    private static final int MAXIMO_POR_IP = 20;
    private static final Duration VENTANA_LIMITE = Duration.ofMinutes(15);

    private final AdministradorRepositorio administradores;
    private final TokenRefrescoAdminRepositorio tokensRefresco;
    private final PasswordEncoder codificador;
    private final ServicioJwt jwt;
    private final PropiedadesJwt propiedades;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();
    private final LimitadorIntentos limitePorUsuario;
    private final LimitadorIntentos limitePorIp;
    private final RevocacionInmediata revocacion;

    public ServicioAccesoPanel(AdministradorRepositorio administradores,
                               TokenRefrescoAdminRepositorio tokensRefresco,
                               PasswordEncoder codificador,
                               ServicioJwt jwt,
                               PropiedadesJwt propiedades,
                               Clock reloj,
                               RevocacionInmediata revocacion) {
        this.administradores = administradores;
        this.tokensRefresco = tokensRefresco;
        this.codificador = codificador;
        this.jwt = jwt;
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.revocacion = revocacion;
        this.limitePorUsuario = new LimitadorIntentos(MAXIMO_POR_USUARIO, VENTANA_LIMITE, reloj);
        this.limitePorIp = new LimitadorIntentos(MAXIMO_POR_IP, VENTANA_LIMITE, reloj);
    }

    public SesionEmitida acceder(String usuario, String contrasena, String ip) {
        String clave = usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
        exigirMargen(limitePorIp.registrar(ip));
        exigirMargen(limitePorUsuario.registrar(clave));

        Optional<Administrador> encontrado = administradores.findByUsuario(clave);
        // Se verifica siempre, exista o no: el coste de BCrypt tiene que gastarse
        // en ambos caminos para que no se distingan por tiempo.
        String hash = encontrado.map(Administrador::getHashContrasena).orElse(HASH_SENUELO);
        boolean coincide = codificador.matches(contrasena, hash);

        Administrador administrador = encontrado
                .filter(a -> coincide && a.isActivo())
                .orElseThrow(ServicioAccesoPanel::credencialesInvalidas);

        limitePorUsuario.olvidar(clave);
        Instant ahora = reloj.instant();
        administrador.registrarAcceso(ahora);
        return emitir(administrador, UUID.randomUUID(), ahora);
    }

    /**
     * Rota el refresco: el presentado queda usado y se emite uno nuevo en la
     * misma familia.
     */
    public SesionEmitida refrescar(String tokenPresentado) {
        Instant ahora = reloj.instant();
        TokenRefrescoAdmin token = tokensRefresco.findByHashToken(hashDe(tokenPresentado))
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED,
                        "La sesión no es válida. Vuelve a entrar."));

        if (token.fueUsado()) {
            // Un refresco ya usado que reaparece solo tiene una explicación:
            // alguien tiene una copia. No se sabe si el legítimo es este o el
            // otro, así que caen los dos.
            revocacion.revocarFamilia(token.getFamilia(), ahora);
            log.warn("Reutilización de token de refresco detectada. Familia revocada: {}", token.getFamilia());
            throw new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED,
                    "La sesión se cerró por seguridad. Vuelve a entrar.");
        }
        if (!token.estaVigente(ahora)) {
            throw new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED,
                    "La sesión expiró. Vuelve a entrar.");
        }

        Administrador administrador = token.getAdministrador();
        if (!administrador.isActivo()) {
            // Desactivar a alguien tiene que cortarle el acceso ahora, no
            // cuando caduque su token.
            revocacion.revocarFamilia(token.getFamilia(), ahora);
            throw credencialesInvalidas();
        }

        token.marcarUsado(ahora);
        return emitir(administrador, token.getFamilia(), ahora);
    }

    /**
     * Cierra la sesión revocando la familia completa, no solo el token
     * presentado: si quedara viva la cadena, cerrar sesión no cerraría nada.
     */
    public void cerrarSesion(String tokenPresentado) {
        if (tokenPresentado == null || tokenPresentado.isBlank()) {
            return;
        }
        tokensRefresco.findByHashToken(hashDe(tokenPresentado))
                .ifPresent(token -> tokensRefresco.revocarFamilia(token.getFamilia(), reloj.instant()));
    }

    /** Corta todas las sesiones de un administrador. Se invoca al desactivarlo. */
    public void revocarSesionesDe(Long idAdministrador) {
        tokensRefresco.revocarTodosDe(idAdministrador, reloj.instant());
    }

    public Duration vigenciaRefresco() {
        return propiedades.vigenciaRefrescoPanel();
    }

    public boolean cookieSegura() {
        return propiedades.cookieSegura();
    }

    // ------------------------------------------------------------------ apoyo

    private SesionEmitida emitir(Administrador administrador, UUID familia, Instant ahora) {
        String refrescoPlano = generarTokenRefresco();
        tokensRefresco.save(new TokenRefrescoAdmin(
                administrador,
                hashDe(refrescoPlano),
                familia,
                ahora.plus(propiedades.vigenciaRefrescoPanel()),
                ahora));

        return new SesionEmitida(
                jwt.emitirAccesoPanel(administrador),
                jwt.segundosDeVigenciaAcceso(),
                refrescoPlano,
                administrador);
    }

    private String generarTokenRefresco() {
        byte[] bytes = new byte[BYTES_TOKEN_REFRESCO];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, no BCrypt. Un token de refresco tiene 256 bits de entropía real,
     * así que no hay nada que forzar por diccionario y no hace falta un hash
     * lento: lo que hace falta es poder buscarlo por igualdad, y BCrypt no
     * permite eso porque cada hash lleva su propia sal.
     */
    private static String hashDe(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }

    private static void exigirMargen(long segundosDeEspera) {
        if (segundosDeEspera > 0) {
            throw new ExcepcionAplicacion(CodigoError.TOO_MANY_REQUESTS,
                    "Demasiados intentos. Inténtalo de nuevo en " + segundosDeEspera + " segundos.")
                    .con("reintentarEn", segundosDeEspera);
        }
    }

    private static ExcepcionAplicacion credencialesInvalidas() {
        return new ExcepcionAplicacion(CodigoError.INVALID_CREDENTIALS, "Usuario o contraseña incorrectos.");
    }

    /** Lo que sale de un acceso o de un refresco. */
    public record SesionEmitida(String tokenAcceso, long expiraEnSegundos, String tokenRefresco,
                                Administrador administrador) {
    }
}
