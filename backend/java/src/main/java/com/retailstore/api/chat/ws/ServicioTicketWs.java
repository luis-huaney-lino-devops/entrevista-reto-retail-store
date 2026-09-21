package com.retailstore.api.chat.ws;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Tickets de un solo uso para abrir el WebSocket.
 *
 * <p><strong>Por qué no se manda el JWT en la URL.</strong> El navegador no
 * permite poner cabeceras en el apretón de manos de un WebSocket, así que la
 * credencial tiene que ir en la dirección. Y las direcciones acaban en los
 * registros del servidor, en los del proxy y en el historial: un token de
 * acceso ahí es un token de acceso filtrado.
 *
 * <p>Con un ticket, lo que se filtra es una cadena que ya se usó, que caducó a
 * los treinta segundos y que no sirve para nada más. El JWT viaja por la
 * cabecera {@code Authorization} de una petición normal, como el resto.
 */
@Service
public class ServicioTicketWs {

    private static final Duration VIGENCIA = Duration.ofSeconds(30);
    private static final int BYTES = 24;

    private final Map<String, Vale> vales = new ConcurrentHashMap<>();
    private final SecureRandom aleatorio = new SecureRandom();
    private final Clock reloj;

    public ServicioTicketWs(Clock reloj) {
        this.reloj = reloj;
    }

    private record Vale(String usuario, Instant expiraEn) {
    }

    public String emitir(String usuario) {
        purgar();
        byte[] bytes = new byte[BYTES];
        aleatorio.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        vales.put(ticket, new Vale(usuario, reloj.instant().plus(VIGENCIA)));
        return ticket;
    }

    /** Lo canjea y lo invalida. Un ticket vale exactamente una conexión. */
    public String canjear(String ticket) {
        Vale vale = ticket == null ? null : vales.remove(ticket);
        if (vale == null || reloj.instant().isAfter(vale.expiraEn())) {
            throw new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED,
                    "El ticket de conexión no es válido o caducó.");
        }
        return vale.usuario();
    }

    public long segundosDeVigencia() {
        return VIGENCIA.toSeconds();
    }

    /** Los que nadie usó. Sin esto el mapa crece con cada pestaña que se abre. */
    private void purgar() {
        Instant ahora = reloj.instant();
        vales.entrySet().removeIf(entrada -> ahora.isAfter(entrada.getValue().expiraEn()));
    }
}
