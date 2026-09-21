package com.retailstore.api.carrito.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.catalogo.dominio.ProductoDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CarritoTest {

    @Test
    @DisplayName("agregar dos veces el mismo producto suma en una sola línea_RN031")
    void unProductoUnaLinea() {
        Carrito carrito = new Carrito();
        Producto producto = ProductoDePrueba.conId(1L, "10.00");

        carrito.agregarOIncrementar(producto, 2);
        carrito.agregarOIncrementar(producto, 3);

        assertThat(carrito.getItems()).hasSize(1);
        assertThat(carrito.getItems().get(0).getCantidad()).isEqualTo(5);
        assertThat(carrito.totalUnidades()).isEqualTo(5);
    }

    @Test
    @DisplayName("productos distintos ocupan líneas distintas_RN031")
    void productosDistintosLineasDistintas() {
        Carrito carrito = new Carrito();

        carrito.agregarOIncrementar(ProductoDePrueba.conId(1L, "10.00"), 1);
        carrito.agregarOIncrementar(ProductoDePrueba.conId(2L, "20.00"), 1);

        assertThat(carrito.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("dos productos SIN id no se confunden entre sí")
    void productosSinIdNoSeConfunden() {
        Carrito carrito = new Carrito();

        carrito.agregarOIncrementar(ProductoDePrueba.nuevo("a", "10.00"), 1);
        carrito.agregarOIncrementar(ProductoDePrueba.nuevo("b", "20.00"), 1);

        // Con Objects.equals(null, null) ambos serían "el mismo producto" y
        // acabarían en una sola línea con cantidad 2.
        assertThat(carrito.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("buscar una línea que no está devuelve vacío_RN037")
    void lineaAjenaNoSeEncuentra() {
        Carrito carrito = new Carrito();
        carrito.agregarOIncrementar(ProductoDePrueba.conId(1L, "10.00"), 1);

        assertThat(carrito.buscarItem(999L)).isEmpty();
    }

    @Test
    @DisplayName("eliminar una línea inexistente devuelve false_RN037")
    void eliminarLineaInexistente() {
        Carrito carrito = new Carrito();

        assertThat(carrito.eliminarItem(999L)).isFalse();
    }

    @Test
    @DisplayName("el total de línea es precio por cantidad_RN032")
    void totalDeLinea() {
        Carrito carrito = new Carrito();
        ItemCarrito item = carrito.agregarOIncrementar(ProductoDePrueba.conId(1L, "149.90"), 2);

        assertThat(item.totalLinea()).isEqualByComparingTo("299.80");
    }

    @Test
    @DisplayName("vaciar quita las líneas y el cupón")
    void vaciar() {
        Carrito carrito = new Carrito();
        carrito.agregarOIncrementar(ProductoDePrueba.conId(1L, "10.00"), 1);

        carrito.vaciar();

        assertThat(carrito.estaVacio()).isTrue();
        assertThat(carrito.getCupon()).isNull();
    }

}
