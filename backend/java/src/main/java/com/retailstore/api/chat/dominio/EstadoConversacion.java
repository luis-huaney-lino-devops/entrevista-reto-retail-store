package com.retailstore.api.chat.dominio;

public enum EstadoConversacion {
    /** Admite mensajes nuevos. */
    ABIERTA,
    /** Se atendió. Queda consultable, pero nadie puede escribir en ella. */
    CERRADA
}
