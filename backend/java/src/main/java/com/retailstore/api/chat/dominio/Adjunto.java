package com.retailstore.api.chat.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un archivo adjunto a un mensaje del chat.
 *
 * <p>Tabla aparte de {@code archivo} porque aquí sí entra un PDF, y
 * {@code archivo} tiene un CHECK que solo admite WebP. Mezclarlos obligaría a
 * relajar esa restricción y se perdería la garantía de que toda imagen del
 * catálogo está convertida.
 *
 * <p>Se crea <strong>antes</strong> que el mensaje: primero se sube y se
 * valida, y solo después se envía el mensaje que lo referencia. Por eso
 * {@code mensaje} admite nulo mientras el adjunto está huérfano.
 */
@Entity
@Table(name = "adjunto")
public class Adjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_adjunto")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_id_mensaje")
    private Mensaje mensaje;

    @Column(nullable = false, unique = true, length = 300)
    private String clave;

    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;

    /** Solo {@code image/webp} o {@code application/pdf}. */
    @Column(name = "tipo_mime", nullable = false, length = 60)
    private String tipoMime;

    @Column(nullable = false)
    private long bytes;

    @Column(name = "url_publica", nullable = false, length = 500)
    private String urlPublica;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "creado_por", nullable = false, updatable = false, length = 60)
    private String creadoPor;

    protected Adjunto() {
        // requerido por JPA
    }

    public Adjunto(String clave, String nombreOriginal, String tipoMime, long bytes,
                   String urlPublica, Instant creadoEn, String creadoPor) {
        this.clave = clave;
        this.nombreOriginal = nombreOriginal;
        this.tipoMime = tipoMime;
        this.bytes = bytes;
        this.urlPublica = urlPublica;
        this.creadoEn = creadoEn;
        this.creadoPor = creadoPor;
    }

    void asignarA(Mensaje mensaje) {
        this.mensaje = mensaje;
    }

    public boolean esImagen() {
        return "image/webp".equals(tipoMime);
    }

    public boolean estaHuerfano() {
        return mensaje == null;
    }

    public Long getId() {
        return id;
    }

    public Mensaje getMensaje() {
        return mensaje;
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

    public String getUrlPublica() {
        return urlPublica;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public String getCreadoPor() {
        return creadoPor;
    }
}
