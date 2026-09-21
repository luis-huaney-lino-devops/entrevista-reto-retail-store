package com.retailstore.api.archivo.dominio;

import com.retailstore.api.comun.auditoria.EntidadCreada;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Una imagen ya procesada: convertida a WebP, con sus variantes, guardada en el
 * almacén de objetos.
 *
 * <p><strong>Un archivo es inmutable</strong> (RN-075): no se edita, se
 * reemplaza. Cambiar la imagen de un producto sube una nueva y desreferencia la
 * anterior. Así una URL que ya está cacheada en el navegador de alguien nunca
 * devuelve una imagen distinta de la que se cacheó.
 */
@Entity
@Table(name = "archivo")
public class Archivo extends EntidadCreada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_archivo")
    private Long id;

    /** Ruta dentro del almacén. Opaca para el cliente, que solo ve la URL. */
    @Column(nullable = false, unique = true, length = 300)
    private String clave;

    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;

    @Column(name = "tipo_mime", nullable = false, length = 60)
    private String tipoMime;

    @Column(nullable = false)
    private long bytes;

    @Column(nullable = false)
    private int ancho;

    @Column(nullable = false)
    private int alto;

    /**
     * Obligatorio (RN-074). Una imagen sin alternativa textual es invisible
     * para un lector de pantalla y para el buscador.
     */
    @Column(name = "texto_alt", nullable = false, length = 200)
    private String textoAlt;

    /** SHA-256 del original normalizado. Es lo que permite deduplicar. */
    @Column(name = "hash_contenido", nullable = false, length = 64)
    private String hashContenido;

    @Column(name = "url_publica", nullable = false, length = 500)
    private String urlPublica;

    @OneToMany(mappedBy = "archivo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ancho asc")
    private List<VarianteArchivo> variantes = new ArrayList<>();

    protected Archivo() {
        // requerido por JPA
    }

    public Archivo(String clave, String nombreOriginal, String tipoMime, long bytes,
                   int ancho, int alto, String textoAlt, String hashContenido, String urlPublica) {
        this.clave = clave;
        this.nombreOriginal = nombreOriginal;
        this.tipoMime = tipoMime;
        this.bytes = bytes;
        this.ancho = ancho;
        this.alto = alto;
        this.textoAlt = textoAlt;
        this.hashContenido = hashContenido;
        this.urlPublica = urlPublica;
    }

    public void agregarVariante(VarianteArchivo variante) {
        variante.asignarA(this);
        variantes.add(variante);
    }

    public Optional<VarianteArchivo> variante(TamanoVariante tamano) {
        return variantes.stream().filter(v -> v.getNombre() == tamano).findFirst();
    }

    /** La URL del tamaño pedido, o la del archivo completo si esa variante no existe. */
    public String urlDe(TamanoVariante tamano) {
        return variante(tamano).map(VarianteArchivo::getUrlPublica).orElse(urlPublica);
    }

    // ----- identidad -----

    /**
     * Dos archivos son el mismo si comparten identificador persistido.
     *
     * <p>Dos archivos <strong>sin</strong> id son dos archivos distintos,
     * aunque ambos ids sean nulos. Comparar con {@code Objects.equals} a secas
     * los haría iguales, y una galería con dos fotos recién subidas se
     * quedaría con una sola.
     */
    public boolean esMismo(Archivo otro) {
        if (this == otro) {
            return true;
        }
        if (otro == null) {
            return false;
        }
        return id != null && Objects.equals(id, otro.id);
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public String getClave() {
        return clave;
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public long getBytes() {
        return bytes;
    }

    public int getAncho() {
        return ancho;
    }

    public int getAlto() {
        return alto;
    }

    public String getTextoAlt() {
        return textoAlt;
    }

    public String getHashContenido() {
        return hashContenido;
    }

    public String getUrlPublica() {
        return urlPublica;
    }

    public List<VarianteArchivo> getVariantes() {
        return Collections.unmodifiableList(variantes);
    }
}
