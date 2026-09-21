package com.retailstore.api.chat.ws;

import com.retailstore.api.chat.dominio.Conversacion;
import com.retailstore.api.chat.repositorio.ConversacionRepositorio;
import com.retailstore.api.config.PropiedadesCors;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Registra el canal del chat y decide quién puede abrirlo.
 *
 * <p>La autenticación ocurre en el apretón de manos, no en cada mensaje: una
 * vez abierta la conexión, el rol y la conversación quedan fijados en los
 * atributos de la sesión y ya no dependen de lo que envíe el cliente.
 */
@Configuration
@EnableWebSocket
public class ConfiguracionWebSocket implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionWebSocket.class);

    private final ManejadorChatWs manejador;
    private final ServicioTicketWs tickets;
    private final ConversacionRepositorio conversaciones;
    private final PropiedadesCors cors;

    public ConfiguracionWebSocket(ManejadorChatWs manejador, ServicioTicketWs tickets,
                                  ConversacionRepositorio conversaciones, PropiedadesCors cors) {
        this.manejador = manejador;
        this.tickets = tickets;
        this.conversaciones = conversaciones;
        this.cors = cors;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registro) {
        registro.addHandler(manejador, "/ws/chat")
                .addInterceptors(new Portero())
                // Los mismos orígenes que el resto de la API. Un WebSocket sin
                // esta lista lo abre cualquier página del navegador de la
                // víctima, porque el apretón de manos no está sujeto a CORS.
                .setAllowedOrigins(cors.origenesPermitidos().toArray(String[]::new));
    }

    /** Decide quién entra y con qué papel. */
    private class Portero implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                                       WebSocketHandler manejador, Map<String, Object> atributos) {
            var parametros = UriComponentsBuilder.fromUri(peticion.getURI()).build().getQueryParams();
            String ticket = parametros.getFirst("ticket");
            String token = parametros.getFirst("conversacion");

            if (ticket != null) {
                try {
                    atributos.put(ManejadorChatWs.ATRIBUTO_ROL, "ADMINISTRADOR");
                    atributos.put(ManejadorChatWs.ATRIBUTO_USUARIO, tickets.canjear(ticket));
                    return true;
                } catch (RuntimeException ex) {
                    log.debug("Ticket de WebSocket rechazado");
                    return false;
                }
            }

            if (token != null) {
                Optional<Conversacion> conversacion = porToken(token);
                if (conversacion.isPresent()) {
                    atributos.put(ManejadorChatWs.ATRIBUTO_ROL, "CLIENTE");
                    atributos.put(ManejadorChatWs.ATRIBUTO_CONVERSACION, conversacion.get().getId());
                    atributos.put("tokenConversacion", token);
                    return true;
                }
            }

            // Devolver false cierra el apretón de manos con un 403 y sin
            // cuerpo: quien prueba tickets no aprende nada de la respuesta.
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                                   WebSocketHandler manejador, Exception excepcion) {
            // Sin nada que hacer: el registro de la sesión ocurre cuando la
            // conexión ya está establecida.
        }

        private Optional<Conversacion> porToken(String token) {
            try {
                return conversaciones.findByTokenAcceso(UUID.fromString(token));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
    }
}
