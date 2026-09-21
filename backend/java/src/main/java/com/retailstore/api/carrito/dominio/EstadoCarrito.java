package com.retailstore.api.carrito.dominio;

public enum EstadoCarrito {

    /** Se puede modificar. */
    ACTIVO,

    /** Ya se convirtió en orden. Inmutable desde ese momento. */
    CONVERTIDO
}
