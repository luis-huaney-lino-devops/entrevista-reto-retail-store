package com.retailstore.api.cuenta.servicio;

import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cuenta.dominio.TipoTokenCliente;
import com.retailstore.api.cuenta.dominio.TokenCliente;
import com.retailstore.api.cuenta.repositorio.TokenClienteRepositorio;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite y consume los enlaces de un solo uso que viajan por correo.
 *
 * <p>Concentra aquí lo que tiene que ser igual en verificación y recuperación:
 * cómo se genera el valor, que solo se guarde su hash, cuánto dura y que el
 * anterior deje de servir al emitir uno nuevo.
 */
@Service
@Transactional
public class ServicioTokenCliente {

    /**
     * Media hora. Suficiente para leer un correo y pulsar, corto para que un
     * enlace olvidado en una bandeja no siga abriendo la cuenta.
     */
    public static final Duration VIGENCIA_RECUPERACION = Duration.ofMinutes(30);

    /**
     * Un día para verificar. Aquí el riesgo es otro: el enlace no cambia
     * ninguna credencial, solo confirma que la dirección existe, y obligar a
     * repetir el proceso por tardar una tarde en abrir el correo es fricción
     * sin ganancia de seguridad.
     */
    public static final Duration VIGENCIA_VERIFICACION = Duration.ofHours(24);

    private final TokenClienteRepositorio tokens;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public ServicioTokenCliente(TokenClienteRepositorio tokens, Clock reloj) {
        this.tokens = tokens;
        this.reloj = reloj;
    }

    /**
     * Crea un token y devuelve <strong>el valor en claro</strong>, que es lo
     * único que se puede poner en el correo. En la base solo queda el hash.
     *
     * <p>Invalida antes los vivos del mismo tipo: pedir recuperación tres veces
     * no puede dejar tres enlaces funcionando a la vez.
     */
    public String emitir(Cliente cliente, TipoTokenCliente tipo) {
        Instant ahora = reloj.instant();
        tokens.invalidarVivos(cliente.getId(), tipo, ahora);

        // 32 bytes de entropía. En base64 sin relleno son 43 caracteres que
        // viajan en una URL sin escaparse.
        byte[] crudo = new byte[32];
        azar.nextBytes(crudo);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(crudo);

        Duration vigencia = tipo == TipoTokenCliente.RECUPERAR_CONTRASENA
                ? VIGENCIA_RECUPERACION
                : VIGENCIA_VERIFICACION;

        tokens.save(new TokenCliente(cliente, tipo, hash(valor), ahora, vigencia));
        return valor;
    }

    /**
     * Busca el token, comprueba que sirva y lo consume en el mismo paso.
     *
     * <p>Consumir aquí y no en quien llama es lo que hace que no se pueda usar
     * dos veces: si la comprobación y el consumo estuvieran separados, dos
     * peticiones simultáneas podrían pasar las dos por la comprobación antes de
     * que ninguna consumiera.
     *
     * <p>Devuelve vacío tanto si no existe como si caducó o ya se usó: quien
     * prueba tokens no debe poder distinguir los casos.
     */
    public Optional<Cliente> consumir(String valor, TipoTokenCliente tipo) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        Instant ahora = reloj.instant();
        return tokens.findByHashToken(hash(valor))
                .filter(t -> t.getTipo() == tipo)
                .filter(t -> t.esUtilizable(ahora))
                .map(t -> {
                    t.consumir(ahora);
                    return t.getCliente();
                });
    }

    /**
     * SHA-256 en hexadecimal: 64 caracteres, que es justo lo que admite la
     * columna.
     */
    private static String hash(String valor) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }
}
