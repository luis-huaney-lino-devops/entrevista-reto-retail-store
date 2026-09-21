package com.retailstore.api.ubigeo.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Primer nivel del ubigeo peruano.
 *
 * <p>Sin auditoría y sin eliminación lógica: es un catálogo oficial, no un dato
 * que la aplicación cree o borre. Se siembra con {@code V002} y solo cambia el
 * día que cambie la división política del país, que es una migración.
 */
@Entity
@Table(name = "departamento")
public class Departamento {

    /**
     * {@code smallint} en la base, {@code Short} aquí. No es purismo: con
     * {@code ddl-auto: validate} un {@code Integer} sobre una columna
     * {@code smallint} impide arrancar la aplicación.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_departamento")
    private Short id;

    @Column(nullable = false, length = 60)
    private String nombre;

    protected Departamento() {
        // requerido por JPA
    }

    public Short getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
