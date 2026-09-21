package com.retailstore.api.archivo.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InspectorImagenTest {

    @Test
    @DisplayName("reconoce un PNG por su firma y lee su tamaño de la cabecera_RN070")
    void reconocePng() {
        var cabecera = InspectorImagen.inspeccionar(ImagenDePrueba.png(640, 480));

        assertThat(cabecera.formato()).isEqualTo(InspectorImagen.Formato.PNG);
        assertThat(cabecera.ancho()).isEqualTo(640);
        assertThat(cabecera.alto()).isEqualTo(480);
    }

    @Test
    @DisplayName("un texto con extensión de imagen se rechaza_RN070")
    void rechazaTextoPlano() {
        byte[] texto = "esto no es una imagen aunque se llame foto.png".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> InspectorImagen.inspeccionar(texto))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    @DisplayName("un archivo vacío o diminuto se rechaza_RN070")
    void rechazaArchivoDiminuto() {
        assertThatThrownBy(() -> InspectorImagen.inspeccionar(new byte[] {1, 2, 3}))
                .isInstanceOf(ExcepcionAplicacion.class);
        assertThatThrownBy(() -> InspectorImagen.inspeccionar(null))
                .isInstanceOf(ExcepcionAplicacion.class);
    }

    @Test
    @DisplayName("AVIF se rechaza explícitamente: la JVM no lo decodifica_RN070")
    void rechazaAvif() {
        byte[] avif = new byte[32];
        System.arraycopy("ftypavif".getBytes(StandardCharsets.US_ASCII), 0, avif, 4, 8);

        assertThatThrownBy(() -> InspectorImagen.inspeccionar(avif))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    @DisplayName("lee el tamaño de un WebP extendido (VP8X) sin descomprimirlo_RN071")
    void leeDimensionWebpExtendido() {
        byte[] webp = webpVp8x(1200, 800);

        assertThat(InspectorImagen.detectarFormato(webp)).isEqualTo(InspectorImagen.Formato.WEBP);
        assertThat(InspectorImagen.dimensionWebp(webp)).containsExactly(1200, 800);
    }

    /** WebP con chunk VP8X: guarda ancho-1 y alto-1 en 24 bits little-endian. */
    private static byte[] webpVp8x(int ancho, int alto) {
        byte[] b = new byte[32];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
        System.arraycopy("VP8X".getBytes(StandardCharsets.US_ASCII), 0, b, 12, 4);
        int a = ancho - 1;
        int h = alto - 1;
        b[24] = (byte) a;
        b[25] = (byte) (a >>> 8);
        b[26] = (byte) (a >>> 16);
        b[27] = (byte) h;
        b[28] = (byte) (h >>> 8);
        b[29] = (byte) (h >>> 16);
        return b;
    }
}
