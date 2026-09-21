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

/** Segundo nivel del ubigeo. Su nombre solo es único dentro del departamento. */
@Entity
@Table(name = "provincia")
public class Provincia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_provincia")
    private Short id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_departamento", nullable = false)
    private Departamento departamento;

    @Column(nullable = false, length = 60)
    private String nombre;

    protected Provincia() {
        // requerido por JPA
    }

    public Short getId() {
        return id;
    }

    public Departamento getDepartamento() {
        return departamento;
    }

    public String getNombre() {
        return nombre;
    }
}
