package com.retailstore.api.chat.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailstore.api.chat.servicio.ServicioChat;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * El canal en vivo del chat.
 *
 * <p>Un mensaje que entra por aquí va al <strong>mismo</strong>
 * {@link ServicioChat} que usa el REST: las validaciones, la transacción y la
 * notificación son las mismas. Si el WebSocket tuviera su propio camino de
 * escritura, habría dos sitios donde arreglar cada regla y uno de los dos se
 * quedaría atrás.
 *
 * <p>Protocolo, deliberadamente pequeño:
 *
 * <pre>
 *   → {"tipo":"SUSCRIBIR","conversacionId":12}
 *   → {"tipo":"MENSAJE","conversacionId":12,"cuerpo":"Hola","adjuntoIds":[3]}
 *   → {"tipo":"ESCRIBIENDO","conversacionId":12}
 *   ← {"tipo":"LISTA","rol":"ADMINISTRADOR"}
 *   ← {"tipo":"MENSAJE","conversacionId":12,"mensaje":{...}}
 *   ← {"tipo":"ERROR","code":"...","detalle":"..."}
 * </pre>
 */
@Component
public class ManejadorChatWs extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorChatWs.class);

    /** Tope del búfer de salida por sesión; pasado eso, se corta la conexión. */
    private static final int BUFER_SALIDA = 512 * 1024;
    private static final int ESPERA_ENVIO_MS = 5_000;

    static final String ATRIBUTO_ROL = "rol";
    static final String ATRIBUTO_USUARIO = "usuario";
    static final String ATRIBUTO_CONVERSACION = "conversacionCliente";

    private final ServicioChat chat;
    private final RegistroSesionesWs registro;
    private final ObjectMapper json;

    public ManejadorChatWs(ServicioChat chat, RegistroSesionesWs registro, ObjectMapper json) {
        this.chat = chat;
        this.registro = registro;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession sesionCruda) throws IOException {
        // sendMessage no es seguro entre hilos y aquí escriben varios: el
        // decorador serializa los envíos y corta si el cliente no lee.
        WebSocketSession sesion = new ConcurrentWebSocketSessionDecorator(
                sesionCruda, ESPERA_ENVIO_MS, BUFER_SALIDA);
        sesionCruda.getAttributes().put("envoltura", sesion);

        String rol = (String) sesion.getAttributes().get(ATRIBUTO_ROL);
        if ("ADMINISTRADOR".equals(rol)) {
            registro.registrarPanel(sesion);
        } else {
            Long idConversacion = (Long) sesion.getAttributes().get(ATRIBUTO_CONVERSACION);
            registro.suscribir(idConversacion, sesion);
        }

        responder(sesion, Map.of("tipo", "LISTA", "rol", rol));
    }

    @Override
    protected void handleTextMessage(WebSocketSession sesionCruda, TextMessage mensaje) throws IOException {
        WebSocketSession sesion = envoltura(sesionCruda);
        String rol = (String) sesion.getAttributes().get(ATRIBUTO_ROL);

        try {
            JsonNode nodo = json.readTree(mensaje.getPayload());
            String tipo = texto(nodo, "tipo");

            switch (tipo == null ? "" : tipo) {
                case "SUSCRIBIR" -> suscribir(sesion, rol, nodo);
                case "MENSAJE" -> enviarMensaje(sesion, rol, nodo);
                case "ESCRIBIENDO" -> difundirEscribiendo(sesion, rol, nodo);
                default -> responder(sesion, Map.of(
                        "tipo", "ERROR", "code", "MALFORMED_REQUEST",
                        "detalle", "Tipo de mensaje desconocido: " + tipo));
            }
        } catch (ExcepcionAplicacion ex) {
            // El error vuelve por el propio socket: cerrar la conexión por un
            // mensaje inválido obligaría a reconectar por un error de usuario.
            responder(sesion, Map.of(
                    "tipo", "ERROR",
                    "code", ex.codigo().name(),
                    "detalle", ex.getMessage()));
        } catch (IOException | RuntimeException ex) {
            log.warn("Error tratando un mensaje del chat", ex);
            responder(sesion, Map.of(
                    "tipo", "ERROR", "code", "INTERNAL_ERROR",
                    "detalle", "No se pudo procesar el mensaje."));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesionCruda, CloseStatus estado) {
        registro.olvidar(envoltura(sesionCruda));
    }

    // ------------------------------------------------------------------ casos

    private void suscribir(WebSocketSession sesion, String rol, JsonNode nodo) throws IOException {
        if (!"ADMINISTRADOR".equals(rol)) {
            // El cliente ya está atado a su conversación por el token del
            // apretón de manos: dejarle elegir otra sería dejarle leer hilos
            // ajenos.
            responder(sesion, Map.of("tipo", "ERROR", "code", "FORBIDDEN",
                    "detalle", "Solo el panel elige conversación."));
            return;
        }
        Long id = numero(nodo, "conversacionId");
        chat.buscar(id);
        registro.suscribir(id, sesion);
        responder(sesion, Map.of("tipo", "SUSCRITO", "conversacionId", id));
    }

    private void enviarMensaje(WebSocketSession sesion, String rol, JsonNode nodo) {
        String cuerpo = texto(nodo, "cuerpo");
        List<Long> adjuntos = numeros(nodo, "adjuntoIds");

        if ("ADMINISTRADOR".equals(rol)) {
            chat.responder(numero(nodo, "conversacionId"), cuerpo, adjuntos);
        } else {
            String token = (String) sesion.getAttributes().get("tokenConversacion");
            chat.escribirComoCliente(token, cuerpo, adjuntos);
        }
        // No se responde nada: el mensaje guardado vuelve por la difusión, y
        // así quien lo envió lo ve exactamente igual que el resto.
    }

    private void difundirEscribiendo(WebSocketSession sesion, String rol, JsonNode nodo) {
        Long id = "ADMINISTRADOR".equals(rol)
                ? numero(nodo, "conversacionId")
                : (Long) sesion.getAttributes().get(ATRIBUTO_CONVERSACION);
        registro.aConversacion(id, Map.of(
                "tipo", "ESCRIBIENDO",
                "conversacionId", id,
                "quien", rol));
    }

    // ------------------------------------------------------------------ apoyo

    private void responder(WebSocketSession sesion, Object evento) throws IOException {
        if (sesion.isOpen()) {
            sesion.sendMessage(new TextMessage(json.writeValueAsString(evento)));
        }
    }

    private static WebSocketSession envoltura(WebSocketSession sesionCruda) {
        Object envuelta = sesionCruda.getAttributes().get("envoltura");
        return envuelta instanceof WebSocketSession sesion ? sesion : sesionCruda;
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private static Long numero(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        if (valor == null || !valor.canConvertToLong()) {
            throw new IllegalArgumentException("Falta el campo numérico '" + campo + "'");
        }
        return valor.asLong();
    }

    private static List<Long> numeros(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        if (valor == null || !valor.isArray()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        valor.forEach(elemento -> ids.add(elemento.asLong()));
        return ids;
    }
}
