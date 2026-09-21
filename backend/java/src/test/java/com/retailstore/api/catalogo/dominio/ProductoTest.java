package com.retailstore.api.catalogo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductoTest {

    @Test
    @DisplayName("calcula el porcentaje de descuento redondeado_RN004")
    void calculaDescuento() {
        Producto producto = producto("149.90");
        producto.cambiarPrecios(new BigDecimal("149.90"), new BigDecimal("199.90"));

        assertThat(producto.porcentajeDescuento()).isEqualTo(25);
        assertThat(producto.enOferta()).isTrue();
    }

    @Test
    @DisplayName("sin precio anterior no hay descuento_RN004")
    void sinPrecioAnteriorNoHayDescuento() {
        Producto producto = producto("100.00");

        assertThat(producto.porcentajeDescuento()).isNull();
        assertThat(producto.enOferta()).isFalse();
    }

    @Test
    @DisplayName("precio anterior menor que el precio lanza INVALID_COMPARE_PRICE_RN004")
    void rechazaPrecioAnteriorMenor() {
        Producto producto = producto("100.00");

        assertThatThrownBy(() -> producto.cambiarPrecios(new BigDecimal("100.00"), new BigDecimal("80.00")))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INVALID_COMPARE_PRICE);
    }

    @Test
    @DisplayName("precio anterior igual al precio tampoco es descuento_RN004")
    void rechazaPrecioAnteriorIgual() {
        Producto producto = producto("100.00");

        assertThatThrownBy(() -> producto.cambiarPrecios(new BigDecimal("100.00"), new BigDecimal("100.000")))
                .isInstanceOf(ExcepcionAplicacion.class);
    }

    @Test
    @DisplayName("precio cero o negativo se rechaza_RN003")
    void rechazaPrecioNoPositivo() {
        Producto producto = producto("100.00");

        assertThatThrownBy(() -> producto.cambiarPrecios(BigDecimal.ZERO, null))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("publicar sin imágenes lanza PRODUCT_REQUIRES_IMAGE_RN009")
    void noSePublicaSinImagen() {
        Producto producto = producto("100.00");

        assertThatThrownBy(producto::activar)
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.PRODUCT_REQUIRES_IMAGE);
    }

    @Test
    @DisplayName("publicar en subcategoría inactiva lanza SUBCATEGORY_INACTIVE_RN001")
    void noSePublicaEnSubcategoriaInactiva() {
        Subcategoria subcategoria = subcategoria();
        subcategoria.desactivar();
        Producto producto = new Producto("SKU-1", "Nombre", "nombre", subcategoria, new BigDecimal("10.00"), 1);
        producto.reemplazarImagenes(List.of(archivo()));

        assertThatThrownBy(producto::activar)
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.SUBCATEGORY_INACTIVE);
    }

    @Test
    @DisplayName("con imagen y subcategoría activa se publica_RN009")
    void sePublicaConImagen() {
        Producto producto = producto("100.00");
        producto.reemplazarImagenes(List.of(archivo()));

        producto.activar();

        assertThat(producto.isActivo()).isTrue();
    }

    @Test
    @DisplayName("quitar todas las imágenes despublica el producto_RN009")
    void quitarImagenesDespublica() {
        Producto producto = producto("100.00");
        producto.reemplazarImagenes(List.of(archivo()));
        producto.activar();

        producto.reemplazarImagenes(List.of());

        assertThat(producto.isActivo()).isFalse();
    }

    @Test
    @DisplayName("reemplazar imágenes respeta el orden recibido")
    void respetaElOrdenDeLasImagenes() {
        Producto producto = producto("100.00");
        Archivo primera = archivo();
        Archivo segunda = archivo();

        producto.reemplazarImagenes(List.of(primera, segunda));

        assertThat(producto.getImagenes()).hasSize(2);
        assertThat(producto.getImagenes().get(0).getOrden()).isZero();
        assertThat(producto.getImagenes().get(1).getOrden()).isEqualTo(1);
        assertThat(producto.imagenPrincipal()).containsSame(primera);
    }

    @Test
    @DisplayName("descontar más stock del disponible lanza INSUFFICIENT_STOCK_RN030")
    void noDescuentaMasDelStock() {
        Producto producto = producto("100.00");

        assertThatThrownBy(() -> producto.descontarStock(99))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("dos productos sin id NO son el mismo")
    void dosProductosNuevosNoSonElMismo() {
        Producto uno = producto("100.00");
        Producto otro = producto("100.00");

        assertThat(uno.esMismo(otro)).isFalse();
        assertThat(uno.esMismo(uno)).isTrue();
        assertThat(uno.esMismo(null)).isFalse();
    }

    @Test
    @DisplayName("reenviar las mismas imágenes conserva las filas, no las recrea")
    void reenviarLasMismasImagenesNoRecreaFilas() {
        Producto producto = producto("100.00");
        Archivo primera = archivoConId(1L);
        Archivo segunda = archivoConId(2L);
        producto.reemplazarImagenes(List.of(primera, segunda));
        List<ImagenProducto> antes = List.copyOf(producto.getImagenes());

        producto.reemplazarImagenes(List.of(primera, segunda));

        // Mismos objetos, no copias: guardar un producto sin tocarle las fotos
        // no puede borrar y reinsertar sus filas —chocaba con uq_producto_imagen.
        assertThat(producto.getImagenes()).containsExactlyElementsOf(antes);
    }

    @Test
    @DisplayName("reordenar conserva las filas y cambia solo su posición")
    void reordenarConservaLasFilas() {
        Producto producto = producto("100.00");
        Archivo primera = archivoConId(1L);
        Archivo segunda = archivoConId(2L);
        producto.reemplazarImagenes(List.of(primera, segunda));

        producto.reemplazarImagenes(List.of(segunda, primera));

        assertThat(producto.getImagenes()).extracting(ImagenProducto::getArchivo)
                .containsExactly(segunda, primera);
        assertThat(producto.getImagenes()).extracting(ImagenProducto::getOrden)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("quitar una imagen y añadir otra deja solo las pedidas")
    void altasYBajasEnLaMismaLlamada() {
        Producto producto = producto("100.00");
        Archivo primera = archivoConId(1L);
        Archivo segunda = archivoConId(2L);
        Archivo tercera = archivoConId(3L);
        producto.reemplazarImagenes(List.of(primera, segunda));

        producto.reemplazarImagenes(List.of(segunda, tercera));

        assertThat(producto.getImagenes()).extracting(ImagenProducto::getArchivo)
                .containsExactly(segunda, tercera);
    }

    @Test
    @DisplayName("el mismo archivo repetido en la petición entra una sola vez")
    void archivoRepetidoEntraUnaVez() {
        Producto producto = producto("100.00");
        Archivo unica = archivoConId(1L);

        producto.reemplazarImagenes(List.of(unica, unica));

        // La tabla tiene UNIQUE (producto, archivo): admitir el duplicado solo
        // aplazaría el fallo hasta el volcado.
        assertThat(producto.getImagenes()).hasSize(1);
    }

    @Test
    @DisplayName("dos archivos recién subidos, ambos sin id, son dos imágenes")
    void dosArchivosNuevosNoSeFundenEnUno() {
        Producto producto = producto("100.00");

        producto.reemplazarImagenes(List.of(archivo(), archivo()));

        assertThat(producto.getImagenes()).hasSize(2);
    }

    // ----- apoyo -----

    private static Producto producto(String precio) {
        return new Producto("SKU-1", "Nombre", "nombre", subcategoria(), new BigDecimal(precio), 5);
    }

    private static Subcategoria subcategoria() {
        Categoria categoria = new Categoria("Tecnología", "tecnologia", null, null, 1);
        return new Subcategoria(categoria, "Audio", "audio-tecnologia", null, null, 1);
    }

    private static Archivo archivoConId(long id) {
        Archivo archivo = archivo();
        ReflectionTestUtils.setField(archivo, "id", id);
        return archivo;
    }

    private static Archivo archivo() {
        return new Archivo("clave", "foto.jpg", "image/webp", 1000, 800, 800, "alt", "hash", "http://x/y.webp");
    }
}
