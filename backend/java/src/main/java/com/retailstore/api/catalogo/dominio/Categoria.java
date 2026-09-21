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
 * Agrupación de primer nivel.
 *
 * <p><strong>Una categoría no contiene productos</strong>, solo subcategorías.
 * Es una decisión y no un descuido: si algunos productos colgaran de la
 * categoría y otros de la subcategoría, toda consulta de catálogo tendría dos
 * caminos y todo recuento dos fuentes. Una tienda que necesita «productos
 * sueltos en Tecnología» crea una subcategoría «General».
 */
// Filtra lo eliminado en toda consulta HQL o de criterios, sin que haya que
// acordarse en cada repositorio. NO se aplica a find(id): ahí Hibernate va
// directo a la clave primaria, y por eso los servicios comprueban
// estaEliminado() al buscar por id.
@SQLRestriction("eliminado_en is null")
@Entity
@Table(name = "categoria")
public class Categoria extends EntidadEliminable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria")
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(length = 500)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_id_archivo")
    private Archivo imagen;

    /**
     * Posición en el menú (RN-014). El criterio de negocio no es alfabético:
     * «Ofertas» va primero aunque empiece por O. Sin este campo, la única
     * alternativa es renombrar cosas para que queden donde se quiere.
     */
    @Column(nullable = false)
    private int orden;

    @Column(nullable = false)
    private boolean activa = true;

    protected Categoria() {
        // requerido por JPA
    }

    public Categoria(String nombre, String slug, String descripcion, Archivo imagen, int orden) {
        this.nombre = nombre;
        this.slug = slug;
        this.descripcion = descripcion;
        this.imagen = imagen;
        this.orden = orden;
    }

    // ----- reglas de dominio -----

    public void editar(String nombre, String descripcion, Archivo imagen, int orden) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.imagen = imagen;
        this.orden = orden;
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

    public Archivo getImagen() {
        return imagen;
    }

    public int getOrden() {
        return orden;
    }

    public boolean isActiva() {
        return activa;
    }
}
