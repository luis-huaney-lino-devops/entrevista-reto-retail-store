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
 * Agrupación de segundo nivel. <strong>Aquí cuelgan los productos</strong>
 * (RN-001).
 *
 * <p>El nombre es único dentro de su categoría, no globalmente: «Accesorios»
 * puede existir bajo Tecnología y bajo Deportes. El slug sí es único en toda la
 * tienda -{@code accesorios-tecnologia}, {@code accesorios-deportes}- para que
 * la URL no necesite mencionar al padre.
 */
// Filtra lo eliminado en toda consulta HQL o de criterios, sin que haya que
// acordarse en cada repositorio. NO se aplica a find(id): ahí Hibernate va
// directo a la clave primaria, y por eso los servicios comprueban
// estaEliminado() al buscar por id.
@SQLRestriction("eliminado_en is null")
@Entity
@Table(name = "subcategoria")
public class Subcategoria extends EntidadEliminable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_subcategoria")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_categoria", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(length = 500)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_id_archivo")
    private Archivo imagen;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false)
    private boolean activa = true;

    protected Subcategoria() {
        // requerido por JPA
    }

    public Subcategoria(Categoria categoria, String nombre, String slug, String descripcion,
                        Archivo imagen, int orden) {
        this.categoria = categoria;
        this.nombre = nombre;
        this.slug = slug;
        this.descripcion = descripcion;
        this.imagen = imagen;
        this.orden = orden;
    }

    // ----- reglas de dominio -----

    /**
     * Mover una subcategoría de categoría está permitido y es una operación
     * real: reorganizar el árbol no debería obligar a recrearla y perder los
     * productos.
     */
    public void editar(Categoria categoria, String nombre, String descripcion, Archivo imagen, int orden) {
        this.categoria = categoria;
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

    /** Visible en la tienda solo si su categoría también lo está. */
    public boolean esVisible() {
        return activa && categoria.isActiva();
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public Categoria getCategoria() {
        return categoria;
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
