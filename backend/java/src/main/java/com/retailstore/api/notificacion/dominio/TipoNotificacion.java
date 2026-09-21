package com.retailstore.api.notificacion.dominio;

/**
 * Los hechos que merecen interrumpir a quien está en el panel.
 *
 * <p>La lista es corta a propósito. Una bandeja que avisa de todo se ignora
 * entera en una semana, y entonces no avisa de nada.
 */
public enum TipoNotificacion {

    /** Un producto publicado se quedó en cero. Se sigue ofreciendo y no se puede comprar. */
    STOCK_AGOTADO(SeveridadNotificacion.URGENTE),

    /** Queda poco. Da tiempo a reponer antes de que se agote. */
    STOCK_BAJO(SeveridadNotificacion.AVISO),

    ORDEN_NUEVA(SeveridadNotificacion.INFO),

    ORDEN_CANCELADA(SeveridadNotificacion.AVISO),

    MENSAJE_CLIENTE(SeveridadNotificacion.AVISO);

    private final SeveridadNotificacion severidad;

    TipoNotificacion(SeveridadNotificacion severidad) {
        this.severidad = severidad;
    }

    public SeveridadNotificacion severidad() {
        return severidad;
    }
}
