package com.retailstore.api.carrito.dominio;

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
 * Una línea del carrito.
 *
 * <p><strong>No guarda precio</strong> (RN-032). El carrito refleja siempre el
 * precio vigente del producto: si sube mientras el carrito está abierto, el
 * comprador ve el precio nuevo, que es el que se le va a cobrar. Guardarlo aquí
 * crearía la expectativa de respetarlo, y esa es una promesa de negocio que
 * nadie tomó.
 */
@Entity
@Table(name = "item_carrito")
public class ItemCarrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item_carrito")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_carrito", nullable = false)
    private Carrito carrito;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_id_producto", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private int cantidad;

    protected ItemCarrito() {
        // requerido por JPA
    }

    ItemCarrito(Producto producto, int cantidad) {
        this.producto = producto;
        this.cantidad = cantidad;
    }

    void asignarA(Carrito carrito) {
        this.carrito = carrito;
    }

    void cambiarCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    void incrementar(int cantidad) {
        this.cantidad += cantidad;
    }

    public BigDecimal totalLinea() {
        return producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() {
        return id;
    }

    public Carrito getCarrito() {
        return carrito;
    }

    public Producto getProducto() {
        return producto;
    }

    public int getCantidad() {
        return cantidad;
    }
}
