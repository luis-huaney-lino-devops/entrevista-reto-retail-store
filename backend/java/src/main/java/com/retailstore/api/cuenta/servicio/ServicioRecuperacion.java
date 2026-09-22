package com.retailstore.api.cuenta.servicio;

import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.limite.LimitadorIntentos;
import com.retailstore.api.correo.servicio.CorreoTrasCommit;
import com.retailstore.api.cuenta.dominio.TipoTokenCliente;
import com.retailstore.api.cuenta.repositorio.TokenRefrescoClienteRepositorio;
import com.retailstore.api.seguridad.servicio.PoliticaContrasena;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperar la contraseña y verificar el correo.
 *
 * <p>Las dos operaciones comparten la misma propiedad incómoda: reciben una
 * dirección de correo de alguien que <strong>todavía no ha demostrado ser
 * nadie</strong>. Por eso ninguna revela si esa dirección tiene cuenta.
 */
@Service
@Transactional
public class ServicioRecuperacion {

    private static final Logger log = LoggerFactory.getLogger(ServicioRecuperacion.class);

    // RN-068. El límite por correo es el que protege una cuenta concreta; el
    // de IP frena barridos sobre muchas direcciones a la vez.
    private static final int MAXIMO_POR_EMAIL = 3;
    private static final Duration VENTANA_EMAIL = Duration.ofMinutes(15);
    private static final int MAXIMO_POR_IP = 10;
    private static final Duration VENTANA_IP = Duration.ofHours(1);

    private final ClienteRepositorio clientes;
    private final ServicioTokenCliente tokens;
    private final CorreoTrasCommit correo;
    private final PasswordEncoder codificador;
    private final TokenRefrescoClienteRepositorio refrescos;
    private final Clock reloj;

    private final LimitadorIntentos limitePorEmail;
    private final LimitadorIntentos limitePorIp;

    public ServicioRecuperacion(ClienteRepositorio clientes, ServicioTokenCliente tokens,
                                CorreoTrasCommit correo, PasswordEncoder codificador,
                                TokenRefrescoClienteRepositorio refrescos, Clock reloj) {
        this.clientes = clientes;
        this.tokens = tokens;
        this.correo = correo;
        this.codificador = codificador;
        this.refrescos = refrescos;
        this.reloj = reloj;
        this.limitePorEmail = new LimitadorIntentos(MAXIMO_POR_EMAIL, VENTANA_EMAIL, reloj);
        this.limitePorIp = new LimitadorIntentos(MAXIMO_POR_IP, VENTANA_IP, reloj);
    }

    /**
     * Pide el enlace para poner una contraseña nueva.
     *
     * <p><strong>Responde lo mismo exista o no el correo</strong> (RN-066). Si
     * contestara «esa dirección no está registrada», el endpoint se convertiría
     * en un comprobador de quién tiene cuenta en la tienda, y eso revela
     * hábitos de compra de cualquiera cuyo correo alguien conozca.
     *
     * <p>Que no lance nada cuando el correo no existe es la parte que suele
     * olvidarse: el silencio tiene que ser indistinguible del éxito.
     */
    public void solicitar(String emailPresentado, String ip) {
        String email = Optional.ofNullable(Cliente.normalizar(emailPresentado)).orElse("");
        exigirMargen(limitePorIp.registrar(ip));
        exigirMargen(limitePorEmail.registrar(email));

        Optional<Cliente> encontrado = clientes.findByEmail(email);
        if (encontrado.isEmpty() || !encontrado.get().isActivo()) {
            // Ni excepción ni registro con el correo dentro: un log con la
            // dirección convertiría el archivo de registro en la lista que este
            // endpoint se niega a dar.
            log.info("Solicitud de recuperación sobre un correo sin cuenta activa. No se hace nada (RN-066).");
            return;
        }

        Cliente cliente = encontrado.get();
        String token = tokens.emitir(cliente, TipoTokenCliente.RECUPERAR_CONTRASENA);
        // Tras el commit: si la transacción revirtiera, el enlace ya enviado
        // apuntaría a un token que no existe.
        correo.recuperacion(cliente.getEmail(), cliente.getNombre(), token);
    }

    /**
     * Cambia la contraseña con el token del correo.
     *
     * <p>Al terminar se revocan <strong>todas</strong> las sesiones abiertas.
     * Quien restablece una contraseña suele hacerlo porque sospecha que alguien
     * entró: dejar viva la sesión del intruso vaciaría de sentido el cambio.
     */
    public void restablecer(String token, String contrasenaNueva) {
        PoliticaContrasena.exigir(contrasenaNueva, "contrasena");

        Cliente cliente = tokens.consumir(token, TipoTokenCliente.RECUPERAR_CONTRASENA)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.INVALID_TOKEN,
                        "El enlace no es válido o ya caducó. Pide uno nuevo."));

        cliente.cambiarContrasena(codificador.encode(contrasenaNueva));

        // Quien llega por este camino demostró tener el correo, así que la
        // dirección queda verificada de paso: exigirle después otro enlace para
        // confirmar lo que acaba de confirmar no tiene sentido.
        cliente.verificarEmail();

        refrescos.revocarTodosDe(cliente.getId(), reloj.instant());
    }

    /**
     * Reenvía el correo de verificación.
     *
     * <p>Exige sesión —lo llama quien ya entró y ve el aviso de «sin
     * verificar»—, así que aquí no hay nada que ocultar: si la cuenta ya está
     * verificada se dice, porque quien pregunta es su dueño.
     */
    public void reenviarVerificacion(Long idCliente, String ip) {
        exigirMargen(limitePorIp.registrar(ip));

        Cliente cliente = clientes.buscarActivo(idCliente)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.CUSTOMER_NOT_FOUND,
                        "No existe la cuenta."));

        if (cliente.isEmailVerificado()) {
            throw new ExcepcionAplicacion(CodigoError.EMAIL_ALREADY_VERIFIED,
                    "Tu correo ya está verificado.");
        }

        exigirMargen(limitePorEmail.registrar(cliente.getEmail()));
        String token = tokens.emitir(cliente, TipoTokenCliente.VERIFICAR_EMAIL);
        correo.verificacion(cliente.getEmail(), cliente.getNombre(), token);
    }

    /** Confirma la dirección con el token del correo (RN-063). */
    public void verificar(String token) {
        Cliente cliente = tokens.consumir(token, TipoTokenCliente.VERIFICAR_EMAIL)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.INVALID_TOKEN,
                        "El enlace no es válido o ya caducó. Pide uno nuevo desde tu cuenta."));
        cliente.verificarEmail();
    }

    /** Mismo formato que el resto de limitadores del proyecto (RN-068). */
    private static void exigirMargen(long segundosDeEspera) {
        if (segundosDeEspera > 0) {
            throw new ExcepcionAplicacion(CodigoError.TOO_MANY_REQUESTS,
                    "Demasiados intentos. Inténtalo de nuevo en " + segundosDeEspera + " segundos.")
                    .con("reintentarEn", segundosDeEspera);
        }
    }
}
