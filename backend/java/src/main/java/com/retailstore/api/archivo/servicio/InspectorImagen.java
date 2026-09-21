package com.retailstore.api.archivo.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Qué es realmente el archivo que subieron, y qué tamaño declara.
 *
 * <p>Dos cosas importan aquí y las dos se hacen <strong>antes</strong> de
 * descomprimir:
 *
 * <ol>
 *   <li><strong>El tipo se decide por los bytes, no por la extensión</strong>
 *       (RN-070). La extensión la elige quien sube: no es un dato, es una
 *       sugerencia.</li>
 *   <li><strong>Las dimensiones se leen de la cabecera</strong> (RN-071). Un PNG
 *       de 2 KB puede declarar 50000x50000 y reventar la memoria al
 *       descomprimirse. Comprobar el tamaño después de cargar la imagen es
 *       comprobarlo cuando el daño ya ocurrió.</li>
 * </ol>
 */
public final class InspectorImagen {

    private InspectorImagen() {
    }

    public enum Formato {
        JPEG("image/jpeg"),
        PNG("image/png"),
        WEBP("image/webp");

        private final String tipoMime;

        Formato(String tipoMime) {
            this.tipoMime = tipoMime;
        }

        public String tipoMime() {
            return tipoMime;
        }
    }

    public record Cabecera(Formato formato, int ancho, int alto) {
        public long megapixeles() {
            return (long) ancho * alto;
        }
    }

    /** @throws ExcepcionAplicacion {@code UNSUPPORTED_IMAGE_TYPE} si no es una imagen admitida */
    public static Cabecera inspeccionar(byte[] contenido) {
        Formato formato = detectarFormato(contenido);
        if (formato == null) {
            throw new ExcepcionAplicacion(CodigoError.UNSUPPORTED_IMAGE_TYPE,
                    "El archivo no es una imagen JPEG, PNG o WebP.");
        }
        int[] dimension = formato == Formato.WEBP
                ? dimensionWebp(contenido)
                : dimensionConImageIO(contenido);
        if (dimension == null) {
            throw new ExcepcionAplicacion(CodigoError.UNSUPPORTED_IMAGE_TYPE,
                    "No se pudo leer el tamaño de la imagen; puede estar corrupta.");
        }
        return new Cabecera(formato, dimension[0], dimension[1]);
    }

    /**
     * Firma real del contenido.
     *
     * <p>AVIF queda fuera a propósito: la JVM no trae decodificador y añadir uno
     * nativo por un formato que ningún panel de administración produce no
     * compensa. Un AVIF se rechaza con un mensaje claro, no se acepta a medias.
     */
    public static Formato detectarFormato(byte[] b) {
        if (b == null || b.length < 16) {
            return null;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Formato.JPEG;
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A) {
            return Formato.PNG;
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Formato.WEBP;
        }
        return null;
    }

    /**
     * ImageIO lee la cabecera sin descomprimir el mapa de bits: {@code getWidth}
     * solo consume los primeros bytes del flujo.
     */
    private static int[] dimensionConImageIO(byte[] contenido) {
        try (ImageInputStream flujo = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            if (flujo == null) {
                return null;
            }
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(flujo);
            if (!lectores.hasNext()) {
                return null;
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(flujo, true, true);
                return new int[] {lector.getWidth(0), lector.getHeight(0)};
            } finally {
                lector.dispose();
            }
        } catch (IOException | IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * WebP a mano porque ImageIO no lo conoce. El formato es RIFF y las
     * dimensiones están en el primer chunk, con tres codificaciones posibles:
     *
     * <pre>
     *   VP8X  extendido  ancho-1 y alto-1 en 24 bits little-endian
     *   VP8   con pérdida  14 bits tras el código de sincronía 9d 01 2a
     *   VP8L  sin pérdida  14+14 bits empaquetados tras la firma 2f
     * </pre>
     */
    static int[] dimensionWebp(byte[] b) {
        if (b.length < 30) {
            return null;
        }
        String chunk = new String(b, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        switch (chunk) {
            case "VP8X" -> {
                int ancho = 1 + (leer(b, 24) | leer(b, 25) << 8 | leer(b, 26) << 16);
                int alto = 1 + (leer(b, 27) | leer(b, 28) << 8 | leer(b, 29) << 16);
                return new int[] {ancho, alto};
            }
            case "VP8 " -> {
                if (leer(b, 23) != 0x9D || leer(b, 24) != 0x01 || leer(b, 25) != 0x2A) {
                    return null;
                }
                int ancho = (leer(b, 26) | leer(b, 27) << 8) & 0x3FFF;
                int alto = (leer(b, 28) | leer(b, 29) << 8) & 0x3FFF;
                return new int[] {ancho, alto};
            }
            case "VP8L" -> {
                if (leer(b, 20) != 0x2F) {
                    return null;
                }
                int bits = leer(b, 21) | leer(b, 22) << 8 | leer(b, 23) << 16 | leer(b, 24) << 24;
                int ancho = (bits & 0x3FFF) + 1;
                int alto = ((bits >>> 14) & 0x3FFF) + 1;
                return new int[] {ancho, alto};
            }
            default -> {
                return null;
            }
        }
    }

    private static int leer(byte[] b, int indice) {
        return b[indice] & 0xFF;
    }
}
