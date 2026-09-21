package com.retailstore.api.catalogo.dominio;

import com.retailstore.api.archivo.dominio.Archivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Une un producto con un archivo, en una posición. La primera es la principal. */
@Entity
@Table(name = "producto_imagen")
public class ImagenProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto_imagen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_producto", nullable = false)
    private Producto producto;

    // EAGER a propósito: una imagen de producto sin su archivo no sirve de
    // nada, así que diferirla solo garantiza una consulta extra por fila.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_id_archivo", nullable = false)
    private Archivo archivo;

    @Column(nullable = false)
    private int orden;

    protected ImagenProducto() {
        // requerido por JPA
    }

    public ImagenProducto(Archivo archivo, int orden) {
        this.archivo = archivo;
        this.orden = orden;
    }

    void asignarA(Producto producto) {
        this.producto = producto;
    }

    void moverA(int orden) {
        this.orden = orden;
    }

    public Long getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public Archivo getArchivo() {
        return archivo;
    }

    public int getOrden() {
        return orden;
    }
}
