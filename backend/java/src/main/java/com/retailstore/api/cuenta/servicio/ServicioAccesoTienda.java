package com.retailstore.api.cuenta.servicio;

import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.correo.servicio.CorreoTrasCommit;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.limite.LimitadorIntentos;
import com.retailstore.api.cuenta.dominio.TokenRefrescoCliente;
import com.retailstore.api.cuenta.dto.RegistroPeticion;
import com.retailstore.api.cuenta.repositorio.TokenRefrescoClienteRepositorio;
import com.retailstore.api.seguridad.config.PropiedadesJwt;
import com.retailstore.api.seguridad.servicio.PoliticaContrasena;
import com.retailstore.api.seguridad.servicio.ServicioJwt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identidad de la tienda: registrarse, entrar, refrescar y salir.
 *
 * <p>Es el gemelo de {@code ServicioAccesoPanel} y comparte su diseño -fallos
 * indistinguibles, refresco rotativo, hash del refresco en la tabla- porque el
 * problema es el mismo. Lo que <strong>no</strong> comparte es nada de estado:
 * ni tabla, ni audiencia, ni vigencia. Esa separación es ADR-0008, y es lo que
 * impide que un fallo en el registro publico -abierto a cualquiera- acabe
 * siendo un acceso al panel.
 *
 * <p>Tres detalles que no se ven en la firma de los métodos:
 *
 * <ol>
 *   <li><strong>El registro responde igual exista o no el correo</strong>
 *       (RN-061), y tarda lo mismo: se gasta el BCrypt en los dos caminos. Un
 *       {@code 409} convertiría el endpoint en un comprobador de quién compra
 *       en esta tienda.</li>
 *   <li><strong>Los fallos de acceso son indistinguibles</strong> (RN-067):
 *       correo inexistente, contraseña equivocada, cuenta desactivada y cuenta
 *       que solo entra con Google dan el mismo 401 y tardan lo mismo.</li>
 *   <li><strong>El refresco rota</strong>, y reutilizar uno ya usado tumba la
 *       familia entera.</li>
 * </ol>
 */
@Service
@Transactional
public class ServicioAccesoTienda {

    private static final Logger log = LoggerFactory.getLogger(ServicioAccesoTienda.class);

    /**
     * Hash real de una contraseña que nadie usa. Sirve para gastar el mismo
     * tiempo de CPU cuando el correo no existe: sin esto un acceso fallido es
     * ~100 ms más rápido para un correo desconocido que para uno real, y esa
     * diferencia se mide desde fuera.
     */
    private static final String HASH_SENUELO =
            "{bcrypt}$2a$12$n.J8FZE2PDoPAo159JUo5ejIw8Sa4MeamcjO9jfKAw8iZR6nQXEn.";

    private static final int BYTES_TOKEN_REFRESCO = 32;

    // RN-068. El registro se limita solo por IP: por correo no tendría sentido
    // -el atacante elige el correo- y sí lo tiene frenar el alta masiva.
    private static final int MAXIMO_ACCESO_POR_EMAIL = 5;
    private static final int MAXIMO_ACCESO_POR_IP = 20;
    private static final Duration VENTANA_ACCESO = Duration.ofMinutes(15);
    private static final int MAXIMO_REGISTRO_POR_IP = 5;
    private static final Duration VENTANA_REGISTRO = Duration.ofHours(1);

    private final ClienteRepositorio clientes;
    private final TokenRefrescoClienteRepositorio tokensRefresco;
    private final PasswordEncoder codificador;
    private final ServicioJwt jwt;
    private final PropiedadesJwt propiedades;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();
    private final LimitadorIntentos limitePorEmail;
    private final LimitadorIntentos limitePorIp;
    private final LimitadorIntentos limiteRegistroPorIp;
    private final CorreoTrasCommit correo;
    private final RevocacionInmediataCliente revocacion;

    public ServicioAccesoTienda(ClienteRepositorio clientes,
                                TokenRefrescoClienteRepositorio tokensRefresco,
                                PasswordEncoder codificador,
                                ServicioJwt jwt,
                                PropiedadesJwt propiedades,
                                Clock reloj,
                                RevocacionInmediataCliente revocacion,
                                CorreoTrasCommit correo) {
        this.clientes = clientes;
        this.tokensRefresco = tokensRefresco;
        this.codificador = codificador;
        this.jwt = jwt;
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.revocacion = revocacion;
        this.correo = correo;
        this.limitePorEmail = new LimitadorIntentos(MAXIMO_ACCESO_POR_EMAIL, VENTANA_ACCESO, reloj);
        this.limitePorIp = new LimitadorIntentos(MAXIMO_ACCESO_POR_IP, VENTANA_ACCESO, reloj);
        this.limiteRegistroPorIp = new LimitadorIntentos(MAXIMO_REGISTRO_POR_IP, VENTANA_REGISTRO, reloj);
    }

    /**
     * Alta de cuenta. <strong>No devuelve nada y nunca falla por duplicado</strong>
     * (RN-061).
     *
     * <p>Cuando el correo ya existe no se toca la cuenta y no se dice. El
     * correo que en su día avisará al dueño -«alguien intentó registrarse con
     * tu correo»- es lo que cierra el hueco de usabilidad, y llega con el
     * bloque de correo.
     */
    public void registrar(RegistroPeticion peticion, String ip) {
        exigirMargen(limiteRegistroPorIp.registrar(ip));
        PoliticaContrasena.exigir(peticion.contrasena(), "contrasena");

        String email = Cliente.normalizar(peticion.email());
        // Se hashea SIEMPRE, también cuando el correo ya existe: es lo que hace
        // que las dos respuestas tarden lo mismo. Sin esto, un 202 rápido
        // significa «ese correo ya tiene cuenta».
        String hash = codificador.encode(peticion.contrasena());

        var existente = clientes.findByEmail(email);
        if (existente.isPresent()) {
            log.info("Registro sobre un correo ya existente. No se modifica nada (RN-061).");
            // La otra mitad de RN-061: la respuesta no revela que el correo
            // existe, y quien sí lo tiene se entera por aquí en vez de quedarse
            // sin saber por qué «su» alta no llegó.
            correo.intentoDeRegistroDuplicado(email, existente.get().getNombre());
            return;
        }

        Cliente cliente = new Cliente(email, peticion.nombre().trim(), normalizarTelefono(peticion.telefono()));
        cliente.cambiarContrasena(hash);
        // save() y no flush(): es una entidad nueva y nadie la ha metido en la
        // sesión todavía. Un flush() a secas no escribiría nada.
        clientes.save(cliente);

        // Tras el commit: si algo revienta después, no queda alguien con un
        // correo de bienvenida a una cuenta que no existe.
        correo.bienvenida(cliente.getEmail(), cliente.getNombre());
    }

    public SesionEmitida acceder(String emailPresentado, String contrasena, String ip) {
        String clave = Optional.ofNullable(Cliente.normalizar(emailPresentado)).orElse("");
        exigirMargen(limitePorIp.registrar(ip));
        exigirMargen(limitePorEmail.registrar(clave));

        Optional<Cliente> encontrado = clientes.findByEmail(clave);
        // Quien entró con Google no tiene hash. Se verifica igualmente contra
        // el señuelo para que su intento tarde lo mismo que cualquier otro.
        String hash = encontrado.map(Cliente::getHashContrasena).orElse(null);
        boolean coincide = codificador.matches(contrasena, hash == null ? HASH_SENUELO : hash);

        Cliente cliente = encontrado
                .filter(c -> coincide && c.isActivo() && c.tieneContrasena())
                .orElseThrow(ServicioAccesoTienda::credencialesInvalidas);

        limitePorEmail.olvidar(clave);
        return emitirPara(cliente);
    }

    /**
     * Emite la sesión de un cliente ya identificado, por contraseña o por
     * Google. Pública porque {@code ServicioAccesoGoogle} termina aquí: los dos
     * caminos tienen que producir exactamente la misma sesión.
     */
    public SesionEmitida emitirPara(Cliente cliente) {
        Instant ahora = reloj.instant();
        cliente.registrarAcceso(ahora);
        return emitir(cliente, UUID.randomUUID(), ahora);
    }

    /**
     * Rota el refresco: el presentado queda usado y se emite uno nuevo en la
     * misma familia.
     */
    public SesionEmitida refrescar(String tokenPresentado) {
        Instant ahora = reloj.instant();
        TokenRefrescoCliente token = tokensRefresco.findByHashToken(hashDe(tokenPresentado))
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED,
                        "La sesión no es válida. Vuelve a entrar."));

        if (token.fueUsado()) {
            // Un refresco ya usado que reaparece solo tiene una explicación:
            // alguien tiene una copia. No se sabe cuál de los dos es el
            // legítimo, así que caen los dos. Va en REQUIRES_NEW porque la
            // excepción de abajo haría rollback de esta misma transacción y
            // desharía la revocación.
            revocacion.revocarFamilia(token.getFamilia(), ahora);
            log.warn("Reutilización de refresco de tienda detectada. Familia revocada: {}", token.getFamilia());
            throw new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED,
                    "La sesión se cerró por seguridad. Vuelve a entrar.");
        }
        if (!token.estaVigente(ahora)) {
            throw new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED, "La sesión expiró. Vuelve a entrar.");
        }

        Cliente cliente = token.getCliente();
        if (!cliente.isActivo() || cliente.estaEliminado()) {
            // Bloquear a alguien desde el panel tiene que cortarle el acceso
            // ahora, no cuando caduque un refresco de 30 días.
            revocacion.revocarFamilia(token.getFamilia(), ahora);
            throw credencialesInvalidas();
        }

        token.marcarUsado(ahora);
        cliente.registrarAcceso(ahora);
        return emitir(cliente, token.getFamilia(), ahora);
    }

    /**
     * Cierra la sesión revocando la familia completa y no solo el token
     * presentado: si quedara viva la cadena, cerrar sesión no cerraría nada.
     */
    public void cerrarSesion(String tokenPresentado) {
        if (tokenPresentado == null || tokenPresentado.isBlank()) {
            return;
        }
        tokensRefresco.findByHashToken(hashDe(tokenPresentado))
                .ifPresent(token -> tokensRefresco.revocarFamilia(token.getFamilia(), reloj.instant()));
    }

    /**
     * Cierra las demás sesiones del cliente, conservando la que hace la
     * petición. Lo invoca el cambio de contraseña.
     *
     * <p>Cambiar la contraseña tiene que echar a quien no debería estar dentro;
     * echar además a quien la está cambiando no protege de nada y hace que el
     * formulario parezca roto. Si no llega la cookie -no hay sesión que
     * conservar- caen todas.
     */
    public void revocarOtrasSesiones(Long idCliente, String refrescoActual) {
        Instant ahora = reloj.instant();
        UUID familiaActual = refrescoActual == null || refrescoActual.isBlank()
                ? null
                : tokensRefresco.findByHashToken(hashDe(refrescoActual))
                        .map(TokenRefrescoCliente::getFamilia)
                        .orElse(null);

        if (familiaActual == null) {
            tokensRefresco.revocarTodosDe(idCliente, ahora);
        } else {
            tokensRefresco.revocarTodosDeSalvo(idCliente, familiaActual, ahora);
        }
    }

    public Duration vigenciaRefresco() {
        return propiedades.vigenciaRefrescoTienda();
    }

    public boolean cookieSegura() {
        return propiedades.cookieSegura();
    }

    // ------------------------------------------------------------------ apoyo

    private SesionEmitida emitir(Cliente cliente, UUID familia, Instant ahora) {
        String refrescoPlano = generarTokenRefresco();
        tokensRefresco.save(new TokenRefrescoCliente(
                cliente,
                hashDe(refrescoPlano),
                familia,
                ahora.plus(propiedades.vigenciaRefrescoTienda()),
                ahora));

        return new SesionEmitida(
                jwt.emitirAccesoTienda(cliente),
                jwt.segundosDeVigenciaAcceso(),
                refrescoPlano,
                cliente);
    }

    private String generarTokenRefresco() {
        byte[] bytes = new byte[BYTES_TOKEN_REFRESCO];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, no BCrypt. Un token de refresco tiene 256 bits de entropía real:
     * no hay diccionario que probar, así que no hace falta un hash lento. Lo
     * que hace falta es poder buscarlo por igualdad, y BCrypt no lo permite
     * porque cada hash lleva su propia sal.
     */
    private static String hashDe(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }

    private static String normalizarTelefono(String telefono) {
        return telefono == null || telefono.isBlank() ? null : telefono.trim();
    }

    private static void exigirMargen(long segundosDeEspera) {
        if (segundosDeEspera > 0) {
            throw new ExcepcionAplicacion(CodigoError.TOO_MANY_REQUESTS,
                    "Demasiados intentos. Inténtalo de nuevo en " + segundosDeEspera + " segundos.")
                    .con("reintentarEn", segundosDeEspera);
        }
    }

    /** Mensaje único: correo inexistente, contraseña mala y cuenta bloqueada se leen igual. */
    static ExcepcionAplicacion credencialesInvalidas() {
        return new ExcepcionAplicacion(CodigoError.INVALID_CREDENTIALS, "Correo o contraseña incorrectos.");
    }

    /** Lo que sale de un acceso, de un refresco o de un acceso con Google. */
    public record SesionEmitida(String tokenAcceso, long expiraEnSegundos, String tokenRefresco, Cliente cliente) {
    }
}
