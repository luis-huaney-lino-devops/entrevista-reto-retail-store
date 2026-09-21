package com.retailstore.api.archivo.dominio;

/**
 * Los cuatro tamaños que la tienda necesita (RN-073).
 *
 * <p>Se mantiene la proporción y <strong>nunca se recorta</strong>: recortar por
 * el centro decapita productos altos, y una tienda con fotos mal recortadas
 * parece descuidada. El encaje lo resuelve el CSS con {@code object-fit}.
 *
 * <p><strong>Tampoco se amplía.</strong> Si el original tiene 600 px de ancho,
 * la variante DETALLE tiene 600 y no 1200 interpolados: ampliar añade peso sin
 * añadir nitidez.
 */
public enum TamanoVariante {

    /** Tabla del panel, mini-carrito. */
    MINIATURA(160),

    /** Rejilla de productos de la tienda. */
    TARJETA(480),

    /** Página de detalle. */
    DETALLE(1200),

    /** Zoom. Acotado: no es el archivo que subió el administrador. */
    ORIGINAL(2400);

    private final int anchoMaximo;

    TamanoVariante(int anchoMaximo) {
        this.anchoMaximo = anchoMaximo;
    }

    public int anchoMaximo() {
        return anchoMaximo;
    }

    /** El ancho real de esta variante para una imagen dada, sin ampliar nunca. */
    public int anchoPara(int anchoOriginal) {
        return Math.min(anchoMaximo, anchoOriginal);
    }
}
