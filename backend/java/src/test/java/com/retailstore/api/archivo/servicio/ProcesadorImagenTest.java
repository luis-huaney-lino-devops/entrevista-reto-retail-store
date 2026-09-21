package com.retailstore.api.archivo.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.retailstore.api.archivo.dominio.TamanoVariante;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Usa el binario nativo de cwebp que trae scrimage. Si estas pruebas fallan al
 * cambiar de plataforma, el problema es el despliegue, no la lógica.
 */
class ProcesadorImagenTest {

    private final ProcesadorImagen procesador = new ProcesadorImagen();

    @Test
    @DisplayName("genera las cuatro variantes y todas son WebP_RN072_RN073")
    void generaCuatroVariantesWebp() {
        var procesada = procesador.procesar(ImagenDePrueba.png(1600, 1200));

        assertThat(procesada.derivadas()).containsOnlyKeys(TamanoVariante.values());
        for (var derivada : procesada.derivadas().values()) {
            assertThat(esWebp(derivada.contenido())).isTrue();
            assertThat(derivada.bytes()).isPositive();
        }
    }

    @Test
    @DisplayName("cada variante respeta su ancho máximo y la proporción_RN073")
    void respetaAnchosYProporcion() {
        var procesada = procesador.procesar(ImagenDePrueba.png(1600, 1200));

        var miniatura = procesada.derivadas().get(TamanoVariante.MINIATURA);
        assertThat(miniatura.ancho()).isEqualTo(160);
        assertThat(miniatura.alto()).isEqualTo(120);

        assertThat(procesada.derivadas().get(TamanoVariante.TARJETA).ancho()).isEqualTo(480);
        assertThat(procesada.derivadas().get(TamanoVariante.DETALLE).ancho()).isEqualTo(1200);
    }

    @Test
    @DisplayName("nunca amplía: una imagen pequeña conserva su tamaño_RN073")
    void noAmplia() {
        var procesada = procesador.procesar(ImagenDePrueba.png(600, 400));

        assertThat(procesada.ancho()).isEqualTo(600);
        assertThat(procesada.derivadas().get(TamanoVariante.DETALLE).ancho()).isEqualTo(600);
        assertThat(procesada.derivadas().get(TamanoVariante.ORIGINAL).ancho()).isEqualTo(600);
        // La miniatura sí se reduce, porque 160 < 600.
        assertThat(procesada.derivadas().get(TamanoVariante.MINIATURA).ancho()).isEqualTo(160);
    }

    @Test
    @DisplayName("el WebP pesa menos que el PNG de origen_RN072")
    void comprimeDeVerdad() {
        byte[] original = ImagenDePrueba.png(1200, 1200);

        var procesada = procesador.procesar(original);

        assertThat(procesada.original().bytes()).isLessThan(original.length);
    }

    @Test
    @DisplayName("una imagen por debajo del mínimo se rechaza_RN071")
    void rechazaImagenPequena() {
        assertThatThrownBy(() -> procesador.procesar(ImagenDePrueba.png(100, 100)))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.IMAGE_TOO_SMALL);
    }

    @Test
    @DisplayName("el error de tamaño dice el valor enviado y el límite_RN071")
    void elErrorEsAccionable() {
        try {
            procesador.procesar(ImagenDePrueba.png(100, 100));
        } catch (ExcepcionAplicacion ex) {
            assertThat(ex.datos()).containsEntry("valorEnviado", 100);
            assertThat(ex.datos()).containsEntry("minimoPermitido", ProcesadorImagen.DIMENSION_MINIMA);
        }
    }

    /** Firma RIFF....WEBP. */
    private static boolean esWebp(byte[] contenido) {
        return contenido.length > 12
                && contenido[0] == 'R' && contenido[1] == 'I' && contenido[2] == 'F' && contenido[3] == 'F'
                && contenido[8] == 'W' && contenido[9] == 'E' && contenido[10] == 'B' && contenido[11] == 'P';
    }
}
