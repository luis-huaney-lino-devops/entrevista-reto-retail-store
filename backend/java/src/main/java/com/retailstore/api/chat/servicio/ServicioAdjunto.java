package com.retailstore.api.chat.servicio;

import com.retailstore.api.archivo.dominio.TamanoVariante;
import com.retailstore.api.archivo.servicio.AlmacenObjetos;
import com.retailstore.api.archivo.servicio.ProcesadorImagen;
import com.retailstore.api.chat.dominio.Adjunto;
import com.retailstore.api.chat.repositorio.AdjuntoRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subida de adjuntos del chat.
 *
 * <p>Una imagen se convierte a WebP igual que las del catálogo —mismo
 * procesador, misma limpieza de metadatos EXIF, que en una foto enviada por un
 * cliente puede incluir sus coordenadas GPS—. Un PDF se guarda tal cual después
 * de comprobar que no trae contenido activo.
 */
@Service
@Transactional
public class ServicioAdjunto {

    private static final int LARGO_HASH_EN_CLAVE = 12;

    private final AdjuntoRepositorio adjuntos;
    private final ProcesadorImagen procesador;
    private final AlmacenObjetos almacen;
    private final Clock reloj;

    public ServicioAdjunto(AdjuntoRepositorio adjuntos, ProcesadorImagen procesador,
                           AlmacenObjetos almacen, Clock reloj) {
        this.adjuntos = adjuntos;
        this.procesador = procesador;
        this.almacen = almacen;
        this.reloj = reloj;
    }

    public Adjunto subir(byte[] contenido, String nombreOriginal, String autor) {
        String nombre = InspectorAdjunto.nombreSeguro(nombreOriginal);
        InspectorAdjunto.Clase clase = InspectorAdjunto.clasificar(contenido, nombre);

        byte[] aGuardar;
        if (clase == InspectorAdjunto.Clase.IMAGEN) {
            // Solo el tamaño de detalle: un adjunto de chat no necesita cuatro
            // variantes, y generarlas sería trabajo que nadie va a pedir.
            var procesada = procesador.procesar(contenido);
            aGuardar = procesada.derivadas().get(TamanoVariante.DETALLE).contenido();
        } else {
            aGuardar = contenido;
        }

        String clave = clave(aGuardar, clase);
        almacen.guardar(clave, aGuardar, InspectorAdjunto.tipoMime(clase));

        return adjuntos.save(new Adjunto(
                clave,
                nombre,
                InspectorAdjunto.tipoMime(clase),
                aGuardar.length,
                almacen.urlPublica(clave),
                reloj.instant(),
                autor));
    }

    /**
     * Los adjuntos que el mensaje dice llevar, comprobando que estén libres.
     *
     * <p>Un adjunto ya enviado no se puede reutilizar en otro mensaje: dejarlo
     * permitiría que alguien con un id adivinado colgara en su conversación un
     * archivo de otra.
     */
    @Transactional(readOnly = true)
    public List<Adjunto> pendientes(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Adjunto> encontrados = adjuntos.findAllById(ids);
        if (encontrados.size() != ids.size() || !encontrados.stream().allMatch(Adjunto::estaHuerfano)) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR,
                    "Alguno de los adjuntos ya no está disponible. Vuelve a subirlo.");
        }
        return encontrados;
    }

    /** Descarta los adjuntos que se subieron y nunca se enviaron. */
    public int limpiarHuerfanos(java.time.Duration antiguedad) {
        List<Adjunto> viejos = adjuntos.huerfanosAnterioresA(reloj.instant().minus(antiguedad));
        viejos.forEach(adjunto -> almacen.borrar(adjunto.getClave()));
        adjuntos.deleteAll(viejos);
        return viejos.size();
    }

    /**
     * La ruta del adjunto en el almacén.
     *
     * <p>Lleva el hash del contenido, pero <strong>no</strong> es solo el hash:
     * el catálogo deduplica por contenido a propósito —la misma foto de
     * producto subida dos veces es un archivo— y aquí sería un error. Un
     * adjunto pertenece a un mensaje y solo a uno; si dos clientes mandan el
     * mismo PDF, reutilizar la fila colgaría el archivo de uno en la
     * conversación del otro, que es justo lo que impide {@link #pendientes}.
     * Sin el sufijo, además, el segundo envío rompía contra
     * {@code uq_adjunto_clave} con un 500.
     */
    private String clave(byte[] contenido, InspectorAdjunto.Clase clase) {
        ZonedDateTime ahora = ZonedDateTime.ofInstant(reloj.instant(), ZoneOffset.UTC);
        return "chat/%d/%02d/%s-%s%s".formatted(
                ahora.getYear(),
                ahora.getMonthValue(),
                sha256(contenido).substring(0, LARGO_HASH_EN_CLAVE),
                UUID.randomUUID().toString().substring(0, 8),
                InspectorAdjunto.extension(clase));
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }
}
