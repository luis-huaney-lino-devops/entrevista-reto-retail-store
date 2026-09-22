package com.retailstore.api.catalogo.dominio;

import java.lang.reflect.Field;
import java.math.BigDecimal;

/**
 * Fábrica de productos para las pruebas.
 *
 * <p>Vive aquí, en el paquete del dominio, porque asignar el id necesita
 * reflexión sobre un campo privado. La alternativa -un setter de id en la
 * entidad- existiría solo para las pruebas y alguien terminaría llamándolo
 * desde un servicio.
 */
public final class ProductoDePrueba {

    private ProductoDePrueba() {
    }

    /** Un producto persistido, con id, en una subcategoría activa. */
    public static Producto conId(Long id, String precio) {
        Producto producto = nuevo(id == null ? "X" : String.valueOf(id), precio);
        if (id != null) {
            asignarId(producto, id);
        }
        return producto;
    }

    /** Un producto recién construido, sin id, como el que aún no se ha guardado. */
    public static Producto nuevo(String sufijo, String precio) {
        Categoria categoria = new Categoria("Tecnología", "tecnologia", null, null, 1);
        Subcategoria subcategoria = new Subcategoria(categoria, "Audio", "audio-tecnologia", null, null, 1);
        return new Producto("SKU-" + sufijo, "Producto " + sufijo, "producto-" + sufijo,
                subcategoria, new BigDecimal(precio), 100);
    }

    /**
     * Un producto persistido y <strong>publicado</strong>: el estado en el que
     * un producto puede llegar de verdad a un carrito.
     *
     * <p>Marca `activo` por reflexión en vez de llamar a {@code activar()}
     * porque publicar exige al menos una imagen (RN-075), y montar una galería
     * completa en cada prueba de carrito o de checkout añadiría ruido sin
     * probar nada de lo que esas pruebas miran.
     */
    public static Producto publicado(Long id, String precio) {
        Producto producto = conId(id, precio);
        marcarActivo(producto);
        return producto;
    }

    public static void marcarActivo(Producto producto) {
        try {
            Field campo = Producto.class.getDeclaredField("activo");
            campo.setAccessible(true);
            campo.set(producto, true);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("No se pudo marcar el producto como activo en la prueba", ex);
        }
    }

    public static void asignarId(Producto producto, Long id) {
        try {
            Field campo = Producto.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(producto, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("No se pudo asignar el id en la prueba", ex);
        }
    }
}
