package com.retailstore.api.archivo.servicio;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.archivo.dominio.TamanoVariante;
import com.retailstore.api.archivo.dominio.VarianteArchivo;
import com.retailstore.api.archivo.dto.ArchivoRespuesta;
import com.retailstore.api.archivo.repositorio.ArchivoRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subida de imágenes: valida, procesa, sube y registra.
 *
 * <p><strong>Todas las variantes se suben antes de escribir la fila.</strong>
 * El orden importa: si la subida falla a mitad, no queda una fila apuntando a un
 * objeto que no existe. El caso contrario -objeto sin fila- sí puede ocurrir si
 * la transacción revierte después de subir, y es el lado bueno del problema:
 * son huérfanos que ocupan espacio y no rompen nada.
 */
@Service
@Transactional
public class ServicioArchivo {

    /** 12 caracteres hexadecimales: 48 bits, suficiente para que no colisionen. */
    private static final int LARGO_HASH_EN_CLAVE = 12;

    private final ArchivoRepositorio archivos;
    private final ProcesadorImagen procesador;
    private final AlmacenObjetos almacen;
    private final Clock reloj;

    public ServicioArchivo(ArchivoRepositorio archivos, ProcesadorImagen procesador,
                           AlmacenObjetos almacen, Clock reloj) {
        this.archivos = archivos;
        this.procesador = procesador;
        this.almacen = almacen;
        this.reloj = reloj;
    }

    public ArchivoRespuesta subir(byte[] contenido, String nombreOriginal, String textoAlt) {
        if (contenido == null || contenido.length == 0) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR, "El archivo está vacío.");
        }
        if (textoAlt == null || textoAlt.isBlank()) {
            // RN-074. Una imagen sin alternativa textual es invisible para un
            // lector de pantalla, y el panel es el único sitio donde alguien
            // sabe qué se ve en ella.
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR, "El texto alternativo es obligatorio.");
        }
        String hash = sha256(contenido);

        // Deduplicación: subir dos veces la misma foto no crea dos archivos.
        // Se comprueba por el hash del ORIGINAL, antes de procesar, porque
        // procesar es lo caro.
        var existente = archivos.findByHashContenido(hash);
        if (existente.isPresent()) {
            return ArchivoRespuesta.de(existente.get());
        }

        ProcesadorImagen.ImagenProcesada procesada = procesador.procesar(contenido);
        String prefijo = prefijoDeClave(hash);

        // 1) Subir todo.
        Map<TamanoVariante, String> claves = new java.util.EnumMap<>(TamanoVariante.class);
        for (var entrada : procesada.derivadas().entrySet()) {
            String clave = prefijo + "/" + entrada.getKey().name().toLowerCase(Locale.ROOT) + ".webp";
            almacen.guardar(clave, entrada.getValue().contenido(), ProcesadorImagen.TIPO_MIME_SALIDA);
            claves.put(entrada.getKey(), clave);
        }

        // 2) Y solo entonces registrar.
        ProcesadorImagen.Derivada original = procesada.original();
        String claveOriginal = claves.get(TamanoVariante.ORIGINAL);
        Archivo archivo = new Archivo(
                claveOriginal,
                recortar(nombreOriginal, 255),
                ProcesadorImagen.TIPO_MIME_SALIDA,
                original.bytes(),
                procesada.ancho(),
                procesada.alto(),
                recortar(textoAlt, 200),
                hash,
                almacen.urlPublica(claveOriginal));

        for (var entrada : procesada.derivadas().entrySet()) {
            ProcesadorImagen.Derivada derivada = entrada.getValue();
            String clave = claves.get(entrada.getKey());
            archivo.agregarVariante(new VarianteArchivo(
                    entrada.getKey(), clave, almacen.urlPublica(clave),
                    derivada.ancho(), derivada.alto(), derivada.bytes()));
        }

        return ArchivoRespuesta.de(archivos.save(archivo));
    }

    @Transactional(readOnly = true)
    public RespuestaPagina<ArchivoRespuesta> listar(int pagina, int tamanoPagina) {
        return RespuestaPagina.de(
                archivos.findAllByOrderByIdDesc(PageRequest.of(pagina - 1, tamanoPagina)),
                ArchivoRespuesta::de);
    }

    @Transactional(readOnly = true)
    public ArchivoRespuesta porId(Long id) {
        return ArchivoRespuesta.de(buscar(id));
    }

    /**
     * Borra la fila y los objetos.
     *
     * <p>Si el archivo está referenciado por un producto, una marca o una
     * categoría, la clave foránea lo impide y sale {@code FILE_IN_USE}. Ese es
     * el árbitro correcto: comprobarlo antes en el servicio dejaría una ventana
     * entre la comprobación y el borrado.
     */
    public void borrar(Long id) {
        Archivo archivo = buscar(id);
        var clavesABorrar = archivo.getVariantes().stream().map(VarianteArchivo::getClave).toList();

        archivos.delete(archivo);
        // Vaciar aquí fuerza a que la restricción salte ahora, dentro de este
        // método, y no al cerrar la transacción: de lo contrario el fallo
        // llegaría después de haber borrado ya los objetos del almacén.
        archivos.flush();

        clavesABorrar.forEach(almacen::borrar);
    }

    public Archivo referencia(Long id) {
        return buscar(id);
    }

    // ------------------------------------------------------------------ apoyo

    private Archivo buscar(Long id) {
        return archivos.findWithVariantesById(id)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.FILE_NOT_FOUND,
                        "No existe el archivo " + id + "."));
    }

    /**
     * {@code productos/2026/09/a3f2c81e9b4d} — tipo, fecha y hash del contenido.
     *
     * <p>Tres propiedades: la misma imagen produce la misma clave (dedupe), la
     * clave cambia si el contenido cambia (caché eterna sin riesgo) y dos
     * administradores que suban {@code foto.jpg} no se pisan.
     */
    private String prefijoDeClave(String hash) {
        ZonedDateTime ahora = ZonedDateTime.ofInstant(reloj.instant(), ZoneOffset.UTC);
        return "productos/%d/%02d/%s".formatted(
                ahora.getYear(), ahora.getMonthValue(), hash.substring(0, LARGO_HASH_EN_CLAVE));
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }

    private static String recortar(String valor, int largo) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        String limpio = valor.strip();
        return limpio.length() <= largo ? limpio : limpio.substring(0, largo);
    }
}
