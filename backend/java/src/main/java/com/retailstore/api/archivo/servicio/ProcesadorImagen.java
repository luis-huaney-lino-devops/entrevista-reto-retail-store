package com.retailstore.api.archivo.servicio;

import com.retailstore.api.archivo.dominio.TamanoVariante;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * La tubería de imagen: validar, normalizar, derivar y convertir.
 *
 * <pre>
 *   VALIDAR     tipo real, tamaño, dimensiones   (antes de descomprimir)
 *   NORMALIZAR  reorientar por EXIF, quitar metadatos
 *   DERIVAR     miniatura, tarjeta, detalle, original acotado
 *   CONVERTIR   todo a WebP, calidad 82
 * </pre>
 *
 * <p>No sabe nada de almacenamiento ni de base de datos: entra un {@code byte[]}
 * y salen {@code byte[]}. Eso es lo que permite probarla sin R2 y sin
 * PostgreSQL.
 */
@Component
public class ProcesadorImagen {

    /** Por encima de esto casi siempre es un archivo equivocado, no una foto. */
    public static final long BYTES_MAXIMOS = 10L * 1024 * 1024;

    /** Defensa contra la bomba de descompresión. */
    public static final int DIMENSION_MAXIMA = 8000;
    public static final long PIXELES_MAXIMOS = 40_000_000L;

    /** Por debajo se ve borrosa en la tarjeta del listado. */
    public static final int DIMENSION_MINIMA = 200;

    /**
     * Calidad 82. Pesa entre un 25% y un 35% menos que un JPEG equivalente y la
     * diferencia visual no se aprecia en fotografía de producto.
     */
    public static final int CALIDAD_WEBP = 82;

    public static final String TIPO_MIME_SALIDA = "image/webp";

    /** Una imagen ya convertida, lista para subir. */
    public record Derivada(byte[] contenido, int ancho, int alto) {
        public long bytes() {
            return contenido.length;
        }
    }

    public record ImagenProcesada(int ancho, int alto, Map<TamanoVariante, Derivada> derivadas) {
        public Derivada original() {
            return derivadas.get(TamanoVariante.ORIGINAL);
        }
    }

    public ImagenProcesada procesar(byte[] contenido) {
        InspectorImagen.Cabecera cabecera = InspectorImagen.inspeccionar(contenido);
        validarLimites(contenido.length, cabecera);

        ImmutableImage normalizada = cargarNormalizada(contenido);

        Map<TamanoVariante, Derivada> derivadas = new EnumMap<>(TamanoVariante.class);
        for (TamanoVariante tamano : TamanoVariante.values()) {
            derivadas.put(tamano, derivar(normalizada, tamano));
        }
        Derivada original = derivadas.get(TamanoVariante.ORIGINAL);
        return new ImagenProcesada(original.ancho(), original.alto(), derivadas);
    }

    // ------------------------------------------------------------------ pasos

    private static void validarLimites(long bytes, InspectorImagen.Cabecera cabecera) {
        if (bytes > BYTES_MAXIMOS) {
            throw excederTamano(bytes, BYTES_MAXIMOS, "bytes");
        }
        if (cabecera.ancho() > DIMENSION_MAXIMA) {
            throw excederTamano(cabecera.ancho(), DIMENSION_MAXIMA, "ancho");
        }
        if (cabecera.alto() > DIMENSION_MAXIMA) {
            throw excederTamano(cabecera.alto(), DIMENSION_MAXIMA, "alto");
        }
        if (cabecera.megapixeles() > PIXELES_MAXIMOS) {
            throw excederTamano(cabecera.megapixeles(), PIXELES_MAXIMOS, "megapixeles");
        }
        if (cabecera.ancho() < DIMENSION_MINIMA || cabecera.alto() < DIMENSION_MINIMA) {
            throw new ExcepcionAplicacion(CodigoError.IMAGE_TOO_SMALL,
                    "La imagen debe medir al menos " + DIMENSION_MINIMA + " px por lado.")
                    .con("valorEnviado", Math.min(cabecera.ancho(), cabecera.alto()))
                    .con("minimoPermitido", DIMENSION_MINIMA);
        }
    }

    /**
     * Reorienta según EXIF y descarta el resto de metadatos.
     *
     * <p>Lo primero, porque una foto de móvil viene con los píxeles de lado y
     * una etiqueta que dice «gíralo 90°»: los navegadores la respetan y las
     * librerías de escalado no, así que sin esto la miniatura sale tumbada.
     *
     * <p>Lo segundo, porque el EXIF lleva modelo de cámara, fecha y a veces
     * <strong>coordenadas GPS</strong>. Publicar eso es una fuga de privacidad
     * involuntaria, y además son kilobytes en cada descarga.
     */
    private static ImmutableImage cargarNormalizada(byte[] contenido) {
        try {
            ImmutableImage cargada = ImmutableImage.loader()
                    .detectOrientation(true)
                    .fromBytes(contenido);
            // Envolver los píxeles ya rotados en una imagen nueva es lo que
            // garantiza que no sobreviva ningún metadato: no hay que acertar
            // qué etiquetas borrar, simplemente no se copia ninguna.
            return ImmutableImage.wrapAwt(cargada.awt());
        } catch (IOException | RuntimeException ex) {
            throw new ExcepcionAplicacion(CodigoError.UNSUPPORTED_IMAGE_TYPE,
                    "No se pudo procesar la imagen; puede estar corrupta.");
        }
    }

    private static Derivada derivar(ImmutableImage imagen, TamanoVariante tamano) {
        int anchoObjetivo = tamano.anchoPara(imagen.width);
        ImmutableImage escalada = anchoObjetivo == imagen.width
                ? imagen
                : imagen.scaleToWidth(anchoObjetivo);
        return new Derivada(aWebp(escalada), escalada.width, escalada.height);
    }

    private static byte[] aWebp(ImmutableImage imagen) {
        try {
            return imagen.bytes(WebpWriter.DEFAULT.withQ(CALIDAD_WEBP));
        } catch (IOException ex) {
            // cwebp es un binario nativo que scrimage extrae del jar: si falla,
            // es un problema de despliegue, no del archivo que subieron.
            throw new ExcepcionAplicacion(CodigoError.SERVICE_UNAVAILABLE,
                    "No se pudo convertir la imagen a WebP.");
        }
    }

    private static ExcepcionAplicacion excederTamano(long enviado, long maximo, String dimension) {
        return new ExcepcionAplicacion(CodigoError.IMAGE_TOO_LARGE,
                "La imagen supera el límite permitido de " + dimension + ".")
                .con("valorEnviado", enviado)
                .con("maximoPermitido", maximo)
                .con("dimension", dimension);
    }
}
