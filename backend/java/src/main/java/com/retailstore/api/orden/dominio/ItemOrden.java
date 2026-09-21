package com.retailstore.api.orden.dominio;

import com.retailstore.api.catalogo.dominio.Producto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Una línea de una orden.
 *
 * <p><strong>Copia nombre, SKU y precio.</strong> Es lo contrario del ítem de
 * carrito, y por la misma razón vista al revés: el carrito refleja el presente
 * y la orden registra el pasado. Un {@code JOIN} al producto para mostrar una
 * orden vieja es un error que se paga en la primera auditoría contable.
 */
@Entity
@Table(name = "item_orden")
public class ItemOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item_orden")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_orden", nullable = false)
    private Orden orden;

    /** Solo para trazar. Los importes de abajo no salen de aquí. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_producto", nullable = false)
    private Producto producto;

    @Column(name = "nombre_producto", nullable = false, length = 160)
    private String nombreProducto;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "total_linea", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalLinea;

    protected ItemOrden() {
        // requerido por JPA
    }

    public ItemOrden(Producto producto, int cantidad) {
        this.producto = producto;
        this.nombreProducto = producto.getNombre();
        this.sku = producto.getSku();
        this.precioUnitario = producto.getPrecio();
        this.cantidad = cantidad;
        this.totalLinea = producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));
    }

    void asignarA(Orden orden) {
        this.orden = orden;
    }

    public Long getId() {
        return id;
    }

    public Orden getOrden() {
        return orden;
    }

    public Producto getProducto() {
        return producto;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getTotalLinea() {
        return totalLinea;
    }
}
