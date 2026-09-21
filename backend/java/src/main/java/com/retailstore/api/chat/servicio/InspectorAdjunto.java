package com.retailstore.api.chat.servicio;

import com.retailstore.api.archivo.servicio.InspectorImagen;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Qué se acepta como adjunto del chat, y qué se rechaza.
 *
 * <p>Solo imagen o PDF. Nada de documentos de oficina, comprimidos ni
 * ejecutables: son los formatos con los que llega casi todo lo que hace daño, y
 * un chat de atención al cliente no los necesita.
 *
 * <p><strong>Esto no es un antivirus.</strong> Es el filtro barato que se hace
 * antes de guardar nada, y conviene ser explícito sobre lo que cubre y lo que
 * no:
 *
 * <ul>
 *   <li><strong>Cubre</strong> el archivo que miente sobre lo que es —la
 *       extensión la elige quien sube, así que no es un dato— y el PDF con las
 *       construcciones que sirven para ejecutar algo al abrirlo.</li>
 *   <li><strong>No cubre</strong> un PDF que esconda esas construcciones dentro
 *       de un flujo comprimido, ni nada que dependa de una vulnerabilidad del
 *       lector. Para eso hace falta un análisis antivirus real en la tubería,
 *       que es la siguiente pieza y no esta.</li>
 * </ul>
 *
 * <p>Lo que sí garantiza el sistema en todos los casos: el adjunto se sirve con
 * {@code Content-Disposition: attachment} y {@code X-Content-Type-Options:
 * nosniff}, así que el navegador lo descarga en lugar de interpretarlo, y
 * nunca se ejecuta en el servidor.
 */
public final class InspectorAdjunto {

    public static final String TIPO_PDF = "application/pdf";
    public static final long BYTES_MAXIMOS = 8L * 1024 * 1024;

    /**
     * Construcciones de PDF que sirven para que algo ocurra al abrir el
     * archivo. Un PDF de atención al cliente —una factura, un comprobante, una
     * foto de un producto roto— no necesita ninguna.
     */
    private static final List<String> CONSTRUCCIONES_ACTIVAS = List.of(
            "/JavaScript",   // código incrustado
            "/JS",           // lo mismo, abreviado
            "/Launch",       // abre un programa externo
            "/EmbeddedFile", // lleva otro archivo dentro
            "/OpenAction",   // ejecuta algo nada más abrirlo
            "/AA",           // acciones automáticas
            "/RichMedia",    // Flash y similares
            "/XFA");         // formularios con lógica

    /** Qué es el adjunto, una vez inspeccionado. */
    public enum Clase {
        IMAGEN,
        PDF
    }

    private InspectorAdjunto() {
    }

    public static Clase clasificar(byte[] contenido, String nombreOriginal) {
        if (contenido == null || contenido.length == 0) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR, "El archivo está vacío.");
        }
        if (contenido.length > BYTES_MAXIMOS) {
            throw new ExcepcionAplicacion(CodigoError.ATTACHMENT_TOO_LARGE,
                    "El adjunto supera los 8 MB.")
                    .con("valorEnviado", (long) contenido.length)
                    .con("maximoPermitido", BYTES_MAXIMOS)
                    .con("dimension", "bytes");
        }

        if (esPdf(contenido)) {
            exigirPdfInofensivo(contenido, nombreOriginal);
            return Clase.PDF;
        }
        if (InspectorImagen.detectarFormato(contenido) != null) {
            return Clase.IMAGEN;
        }

        throw new ExcepcionAplicacion(CodigoError.UNSUPPORTED_ATTACHMENT_TYPE,
                "Solo se admiten imágenes (JPEG, PNG, WebP) y archivos PDF.");
    }

    /**
     * Cabecera real, no la extensión.
     *
     * <p>La firma puede venir precedida de basura: el estándar permite hasta
     * 1024 bytes antes del {@code %PDF-} y hay lectores que los aceptan. Se
     * busca en esa ventana para no dejar pasar un archivo que el lector sí
     * abriría pero esta comprobación consideraría otra cosa.
     */
    static boolean esPdf(byte[] contenido) {
        int ventana = Math.min(contenido.length, 1024 + 5);
        String inicio = new String(contenido, 0, ventana, StandardCharsets.ISO_8859_1);
        return inicio.contains("%PDF-");
    }

    private static void exigirPdfInofensivo(byte[] contenido, String nombreOriginal) {
        // ISO-8859-1 y no UTF-8: un PDF es binario, y decodificarlo como UTF-8
        // sustituiría los bytes inválidos y podría partir justo el nombre que
        // se está buscando.
        String texto = new String(contenido, StandardCharsets.ISO_8859_1);

        for (String construccion : CONSTRUCCIONES_ACTIVAS) {
            if (texto.contains(construccion)) {
                throw new ExcepcionAplicacion(CodigoError.DANGEROUS_ATTACHMENT,
                        "El PDF contiene contenido activo y no se puede adjuntar. "
                                + "Vuelve a exportarlo como PDF simple o envía una imagen.")
                        .con("construccion", construccion)
                        .con("archivo", nombreOriginal);
            }
        }
    }

    /** Nombre seguro para guardar: sin rutas, sin caracteres de control. */
    public static String nombreSeguro(String nombreOriginal) {
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            return "adjunto";
        }
        // Se corta por ambos separadores: un cliente en Windows envía
        // "C:\\Users\\...\\factura.pdf" como nombre.
        String base = nombreOriginal
                .substring(Math.max(nombreOriginal.lastIndexOf('/'), nombreOriginal.lastIndexOf('\\')) + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        return base.isBlank() ? "adjunto" : base.substring(0, Math.min(base.length(), 120));
    }

    public static String extension(Clase clase) {
        return clase == Clase.PDF ? ".pdf" : ".webp";
    }

    public static String tipoMime(Clase clase) {
        return clase == Clase.PDF ? TIPO_PDF : "image/webp";
    }

    /** Para los mensajes de error y los logs. */
    public static String describir(Clase clase) {
        return clase.name().toLowerCase(Locale.ROOT);
    }
}
