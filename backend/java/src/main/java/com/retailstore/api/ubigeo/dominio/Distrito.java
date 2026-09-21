package com.retailstore.api.ubigeo.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tercer nivel del ubigeo y el único al que apunta una dirección.
 *
 * <p>Que la dirección referencie un distrito real -y no tres columnas de
 * texto- es lo que hace que «Lima / Lima / Miraflores» tenga una sola grafía.
 */
@Entity
@Table(name = "distrito")
public class Distrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_distrito")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_provincia", nullable = false)
    private Provincia provincia;

    @Column(nullable = false, length = 60)
    private String nombre;

    protected Distrito() {
        // requerido por JPA
    }

    public Integer getId() {
        return id;
    }

    public Provincia getProvincia() {
        return provincia;
    }

    public String getNombre() {
        return nombre;
    }
}
