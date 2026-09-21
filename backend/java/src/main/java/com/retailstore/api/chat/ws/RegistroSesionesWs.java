package com.retailstore.api.chat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailstore.api.comun.tiemporeal.DifusorTiempoReal;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Quién está conectado y a qué.
 *
 * <p>Es memoria del proceso, igual que el limitador de intentos: con varias
 * instancias detrás de un balanceador, un mensaje solo llega a quien esté
 * conectado a la misma. La salida es publicar los eventos en Redis y que cada
 * instancia reparta a los suyos; quien produce los eventos no se enteraría,
 * porque habla con {@link DifusorTiempoReal} y no con esta clase.
 */
@Component
public class RegistroSesionesWs implements DifusorTiempoReal {

    private static final Logger log = LoggerFactory.getLogger(RegistroSesionesWs.class);

    private final Map<Long, Set<WebSocketSession>> porConversacion = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> delPanel = ConcurrentHashMap.newKeySet();
    private final ObjectMapper json;

    public RegistroSesionesWs(ObjectMapper json) {
        this.json = json;
    }

    public void registrarPanel(WebSocketSession sesion) {
        delPanel.add(sesion);
    }

    public void suscribir(Long idConversacion, WebSocketSession sesion) {
        porConversacion.computeIfAbsent(idConversacion, clave -> ConcurrentHashMap.newKeySet()).add(sesion);
    }

    public void olvidar(WebSocketSession sesion) {
        delPanel.remove(sesion);
        // Se limpia la entrada vacía: sin esto el mapa acumula una clave por
        // cada conversación que alguien abrió alguna vez.
        porConversacion.values().forEach(sesiones -> sesiones.remove(sesion));
        porConversacion.entrySet().removeIf(entrada -> entrada.getValue().isEmpty());
    }

    @Override
    public void aConversacion(Long idConversacion, Object evento) {
        enviar(porConversacion.getOrDefault(idConversacion, Set.of()), evento);
    }

    @Override
    public void aPanel(Object evento) {
        enviar(delPanel, evento);
    }

    public int conectadosAlPanel() {
        return delPanel.size();
    }

    /**
     * Manda el evento, pero <strong>después del commit</strong> si hay una
     * transacción abierta (RN-083).
     *
     * <p>Difundir dentro de la transacción parece inofensivo y no lo es: el
     * receptor recibe el aviso mientras la fila todavía no está confirmada, y
     * si responde al instante —que es lo que hace un chat— su transacción lee
     * la versión anterior y choca con el bloqueo optimista. Pasó exactamente
     * eso: el panel contestaba al cliente y el mensaje se perdía con un
     * conflicto de concurrencia.
     *
     * <p>El otro motivo es más general: si la transacción acaba revirtiendo,
     * ya se habría anunciado algo que nunca ocurrió.
     */
    private void enviar(Set<WebSocketSession> sesiones, Object evento) {
        if (sesiones.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviarAhora(sesiones, evento);
                }
            });
            return;
        }
        enviarAhora(sesiones, evento);
    }

    private void enviarAhora(Set<WebSocketSession> sesiones, Object evento) {
        String texto;
        try {
            texto = json.writeValueAsString(evento);
        } catch (IOException ex) {
            log.error("No se pudo serializar el evento de tiempo real", ex);
            return;
        }
        for (WebSocketSession sesion : sesiones) {
            try {
                if (sesion.isOpen()) {
                    sesion.sendMessage(new TextMessage(texto));
                }
            } catch (IOException | IllegalStateException ex) {
                // Una sesión rota no puede impedir que el resto reciba el
                // mensaje: se descarta y se sigue.
                log.debug("Sesión caída, se descarta", ex);
                olvidar(sesion);
            }
        }
    }
}
