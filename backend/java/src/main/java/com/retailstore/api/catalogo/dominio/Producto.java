package com.retailstore.api.catalogo.dominio;

import com.retailstore.api.archivo.dominio.Archivo;
import com.retailstore.api.comun.auditoria.EntidadEliminable;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Artículo a la venta. Un producto = un SKU, un precio, un stock. Sin variantes
 * (ADR-0007).
 *
 * <p>Las reglas que dependen solo del producto viven aquí y no en el servicio:
 * el descuento, si se puede publicar, si hay stock. El servicio orquesta; la
 * entidad decide sobre sí misma.
 */
// Filtra lo eliminado en toda consulta HQL o de criterios, sin que haya que
// acordarse en cada repositorio. NO se aplica a find(id): ahí Hibernate va
// directo a la clave primaria, y por eso los servicios comprueban
// estaEliminado() al buscar por id.
@SQLRestriction("eliminado_en is null")
@Entity
@Table(name = "producto")
public class Producto extends EntidadEliminable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Long id;

    /** Único e inmutable tras la creación (RN-002). */
    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(name = "descripcion_corta", length = 300)
    private String descripcionCorta;

    @Column(columnDefinition = "text")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_subcategoria", nullable = false)
    private Subcategoria subcategoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_id_marca")
    private Marca marca;

    /** Dinero siempre BigDecimal / numeric(12,2). Nunca double. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    /** Precio tachado. Null cuando no hay promoción. */
    @Column(name = "precio_anterior", precision = 12, scale = 2)
    private BigDecimal precioAnterior;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private boolean destacado;

    /** Nace inactivo: un producto se crea como borrador y se publica aparte. */
    @Column(nullable = false)
    private boolean activo;

    @Column(name = "calificacion_promedio", nullable = false, precision = 2, scale = 1)
    private BigDecimal calificacionPromedio = BigDecimal.ZERO;

    @Column(name = "calificacion_conteo", nullable = false)
    private int calificacionConteo;

    /**
     * Cuántas veces se ha abierto la ficha en la tienda.
     *
     * <p>Un contador y no una tabla de eventos: para responder «qué se mira
     * más» basta, y una fila por visita a un catálogo público crece sin
     * límite. El precio es que no hay serie temporal; el día que haga falta se
     * añade la tabla y esta columna se queda como acumulado.
     */
    @Column(nullable = false)
    private long vistas;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden asc")
    private List<ImagenProducto> imagenes = new ArrayList<>();

    protected Producto() {
        // requerido por JPA
    }

    public Producto(String sku, String nombre, String slug, Subcategoria subcategoria,
                    BigDecimal precio, int stock) {
        this.sku = sku;
        this.nombre = nombre;
        this.slug = slug;
        this.subcategoria = subcategoria;
        this.precio = precio;
        this.stock = stock;
    }

    // ----- reglas de dominio -----

    /**
     * Porcentaje de descuento redondeado, o null si no hay un precio anterior
     * mayor que el actual.
     */
    public Integer porcentajeDescuento() {
        if (precioAnterior == null || precioAnterior.compareTo(precio) <= 0) {
            return null;
        }
        return precioAnterior.subtract(precio)
                .multiply(BigDecimal.valueOf(100))
                .divide(precioAnterior, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    public boolean tieneStock() {
        return stock > 0;
    }

    public boolean enOferta() {
        return porcentajeDescuento() != null;
    }

    /**
     * Precio y precio anterior van juntos porque su validez es conjunta: el
     * precio anterior solo tiene sentido en relación con el precio, y
     * cambiarlos por separado permite estados intermedios inválidos.
     */
    public void cambiarPrecios(BigDecimal precio, BigDecimal precioAnterior) {
        if (precio == null || precio.signum() <= 0) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR, "El precio debe ser mayor que cero.");
        }
        if (precioAnterior != null && precioAnterior.compareTo(precio) <= 0) {
            // RN-004: un "descuento" que sube el precio es un error de captura,
            // no una promoción. Compararlo con compareTo y no con equals:
            // 100.00 y 100.0 son el mismo importe con distinta escala.
            throw new ExcepcionAplicacion(CodigoError.INVALID_COMPARE_PRICE,
                    "El precio anterior debe ser mayor que el precio actual.")
                    .con("precio", precio)
                    .con("precioAnterior", precioAnterior);
        }
        this.precio = precio;
        this.precioAnterior = precioAnterior;
    }

    public void editarDatos(String nombre, String descripcionCorta, String descripcion,
                            Subcategoria subcategoria, Marca marca, int stock, boolean destacado) {
        this.nombre = nombre;
        this.descripcionCorta = descripcionCorta;
        this.descripcion = descripcion;
        this.subcategoria = subcategoria;
        this.marca = marca;
        this.stock = stock;
        this.destacado = destacado;
    }

    /**
     * Publicar exige al menos una imagen (RN-009). Un producto sin foto en la
     * rejilla es un hueco gris que nadie pulsa; es mejor no publicarlo que
     * publicarlo así.
     */
    public void activar() {
        if (imagenes.isEmpty()) {
            throw new ExcepcionAplicacion(CodigoError.PRODUCT_REQUIRES_IMAGE,
                    "Un producto necesita al menos una imagen para publicarse.")
                    .con("productoId", id);
        }
        if (!subcategoria.isActiva()) {
            throw new ExcepcionAplicacion(CodigoError.SUBCATEGORY_INACTIVE,
                    "No se puede publicar un producto en una subcategoría inactiva.")
                    .con("subcategoriaId", subcategoria.getId())
                    .con("subcategoriaNombre", subcategoria.getNombre());
        }
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    /**
     * Entra o sale de la portada.
     *
     * <p>Se puede destacar un borrador. No es un descuido: la portada solo
     * muestra publicados, así que destacar algo que todavía no lo está es
     * dejarlo preparado, y prohibirlo obligaría a acordarse de volver a
     * marcarlo después de publicar.
     */
    public void destacar(boolean destacado) {
        this.destacado = destacado;
    }

    public void descontarStock(int cantidad) {
        if (cantidad > stock) {
            throw new ExcepcionAplicacion(CodigoError.INSUFFICIENT_STOCK,
                    "Solo quedan " + stock + " unidades de «" + nombre + "».")
                    .con("productoId", id)
                    .con("solicitado", cantidad)
                    .con("disponible", stock);
        }
        this.stock -= cantidad;
    }

    public void reponerStock(int cantidad) {
        this.stock += cantidad;
    }

    // ----- imágenes -----

    /**
     * Deja la galería exactamente como la lista recibida, en ese orden.
     *
     * <p>El panel manda la lista tal como quedó tras arrastrar, no un parche
     * de altas y bajas: aquí se traduce una cosa en la otra.
     *
     * <p>Reconcilia en lugar de vaciar y reconstruir. Parecen equivalentes,
     * pero no lo son: dentro de un mismo volcado Hibernate ejecuta los INSERT
     * antes que los DELETE, así que reenviar una imagen que ya estaba choca
     * contra {@code uq_producto_imagen} —guardar un producto sin tocarle las
     * fotos fallaba por eso—. Reconciliar además conserva la fila de lo que no
     * se movió, en vez de darle un identificador nuevo cada vez que se guarda.
     */
    public void reemplazarImagenes(List<Archivo> archivos) {
        // La tabla no admite el mismo archivo dos veces en un producto, así que
        // repetirlo en la petición no es «ponla dos veces»: es un error del
        // cliente. Se queda la primera aparición, que es la que fija el orden
        // que pidió.
        List<Archivo> deseados = new ArrayList<>();
        for (Archivo archivo : archivos) {
            if (deseados.stream().noneMatch(ya -> ya.esMismo(archivo))) {
                deseados.add(archivo);
            }
        }

        imagenes.removeIf(imagen -> deseados.stream().noneMatch(a -> a.esMismo(imagen.getArchivo())));

        int orden = 0;
        for (Archivo archivo : deseados) {
            ImagenProducto existente = imagenes.stream()
                    .filter(imagen -> archivo.esMismo(imagen.getArchivo()))
                    .findFirst()
                    .orElse(null);
            if (existente != null) {
                existente.moverA(orden);
            } else {
                ImagenProducto imagen = new ImagenProducto(archivo, orden);
                imagen.asignarA(this);
                imagenes.add(imagen);
            }
            orden++;
        }
        // La lista en memoria tiene que quedar como quedará al releerla, o la
        // respuesta de este guardado diría un orden y la siguiente lectura otro.
        imagenes.sort(Comparator.comparingInt(ImagenProducto::getOrden));
        if (imagenes.isEmpty() && activo) {
            // Quitar todas las imágenes de un producto publicado lo dejaría en
            // la tienda sin foto: se despublica antes que permitir eso.
            this.activo = false;
        }
    }

    public Optional<Archivo> imagenPrincipal() {
        return imagenes.stream().findFirst().map(ImagenProducto::getArchivo);
    }

    public List<ImagenProducto> getImagenes() {
        return Collections.unmodifiableList(imagenes);
    }

    // ----- identidad -----

    /**
     * Dos productos son el mismo si comparten identificador persistido.
     *
     * <p>Dos entidades <strong>sin</strong> id no son iguales aunque ambas
     * tengan id nulo: son dos objetos nuevos distintos. Usar
     * {@code Objects.equals} directamente sobre los ids los haría iguales, que
     * es un error silencioso y difícil de encontrar.
     */
    public boolean esMismo(Producto otro) {
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

    public String getSku() {
        return sku;
    }

    public String getNombre() {
        return nombre;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescripcionCorta() {
        return descripcionCorta;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Subcategoria getSubcategoria() {
        return subcategoria;
    }

    public Marca getMarca() {
        return marca;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public BigDecimal getPrecioAnterior() {
        return precioAnterior;
    }

    public int getStock() {
        return stock;
    }

    public boolean isDestacado() {
        return destacado;
    }

    public boolean isActivo() {
        return activo;
    }

    public BigDecimal getCalificacionPromedio() {
        return calificacionPromedio;
    }

    public int getCalificacionConteo() {
        return calificacionConteo;
    }

    public long getVistas() {
        return vistas;
    }
}
