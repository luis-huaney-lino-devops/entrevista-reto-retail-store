package com.retailstore.api.comun.tiemporeal;

/**
 * Por dónde salen los avisos en vivo.
 *
 * <p>Es una interfaz y no la clase del WebSocket para romper el ciclo: el chat
 * y las notificaciones necesitan empujar eventos, y el WebSocket necesita
 * llamar al chat para guardar un mensaje. Con la interfaz en medio, las dos
 * mitades dependen de ella y no una de la otra.
 *
 * <p>También deja el sistema listo para cambiar de transporte —Server-Sent
 * Events, una cola— sin tocar a quien produce los eventos.
 */
public interface DifusorTiempoReal {

    /** A quien esté mirando esa conversación: el panel y el cliente. */
    void aConversacion(Long idConversacion, Object evento);

    /** A todas las sesiones del panel. Para notificaciones y contadores. */
    void aPanel(Object evento);
}
