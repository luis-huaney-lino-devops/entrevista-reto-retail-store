package com.retailstore.api.catalogo.dominio;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.comun.auditoria.EntidadEliminable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * Fabricante o sello del producto.
 *
 * <p>Un producto puede no tener marca (RN-013): muchos artículos de tienda no
 * la tienen, y obligar a inventar una «Genérica» ensucia los datos y el filtro
 * de la tienda.
 */
// Filtra lo eliminado en toda consulta HQL o de criterios, sin que haya que
// acordarse en cada repositorio. NO se aplica a find(id): ahí Hibernate va
// directo a la clave primaria, y por eso los servicios comprueban
// estaEliminado() al buscar por id.
@SQLRestriction("eliminado_en is null")
@Entity
@Table(name = "marca")
public class Marca extends EntidadEliminable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_marca")
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(length = 500)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_id_archivo")
    private Archivo logo;

    @Column(nullable = false)
    private boolean activa = true;

    protected Marca() {
        // requerido por JPA
    }

    public Marca(String nombre, String slug, String descripcion, Archivo logo) {
        this.nombre = nombre;
        this.slug = slug;
        this.descripcion = descripcion;
        this.logo = logo;
    }

    // ----- reglas de dominio -----

    /** El slug no se toca: renombrar no puede romper los enlaces (RN-008). */
    public void editar(String nombre, String descripcion, Archivo logo) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.logo = logo;
    }

    public void activar() {
        this.activa = true;
    }

    public void desactivar() {
        this.activa = false;
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Archivo getLogo() {
        return logo;
    }

    public boolean isActiva() {
        return activa;
    }
}
