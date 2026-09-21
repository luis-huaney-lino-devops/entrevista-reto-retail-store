package com.retailstore.api.archivo.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "archivo_variante")
public class VarianteArchivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_archivo_variante")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_archivo", nullable = false)
    private Archivo archivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TamanoVariante nombre;

    @Column(nullable = false, length = 300)
    private String clave;

    @Column(name = "url_publica", nullable = false, length = 500)
    private String urlPublica;

    @Column(nullable = false)
    private int ancho;

    @Column(nullable = false)
    private int alto;

    @Column(nullable = false)
    private long bytes;

    protected VarianteArchivo() {
        // requerido por JPA
    }

    public VarianteArchivo(TamanoVariante nombre, String clave, String urlPublica, int ancho, int alto, long bytes) {
        this.nombre = nombre;
        this.clave = clave;
        this.urlPublica = urlPublica;
        this.ancho = ancho;
        this.alto = alto;
        this.bytes = bytes;
    }

    void asignarA(Archivo archivo) {
        this.archivo = archivo;
    }

    public Long getId() {
        return id;
    }

    public Archivo getArchivo() {
        return archivo;
    }

    public TamanoVariante getNombre() {
        return nombre;
    }

    public String getClave() {
        return clave;
    }

    public String getUrlPublica() {
        return urlPublica;
    }

    public int getAncho() {
        return ancho;
    }

    public int getAlto() {
        return alto;
    }

    public long getBytes() {
        return bytes;
    }
}
