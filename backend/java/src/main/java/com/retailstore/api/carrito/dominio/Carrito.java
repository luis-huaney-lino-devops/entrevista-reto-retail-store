package com.retailstore.api.carrito.dominio;

import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.comun.auditoria.EntidadAuditable;
import com.retailstore.api.cupon.dominio.Cupon;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Selección temporal de un comprador.
 *
 * <p>El identificador es un UUID que el navegador guarda: así el carrito
 * sobrevive sin cuenta. Un entero secuencial permitiría recorrer los carritos
 * ajenos contando desde uno.
 */
@Entity
@Table(name = "carrito")
public class Carrito extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_carrito")
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_id_cupon")
    private Cupon cupon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoCarrito estado = EstadoCarrito.ACTIVO;

    @OneToMany(mappedBy = "carrito", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemCarrito> items = new ArrayList<>();

    public Carrito() {
        // público: crear un carrito vacío es una operación normal
    }

    // ----- reglas de dominio -----

    /**
     * Un producto ocupa una sola línea (RN-031): si ya está, suma cantidades en
     * lugar de añadir otra fila.
     */
    public ItemCarrito agregarOIncrementar(Producto producto, int cantidad) {
        Optional<ItemCarrito> existente = items.stream()
                .filter(item -> esElMismoProducto(item.getProducto(), producto))
                .findFirst();

        if (existente.isPresent()) {
            existente.get().incrementar(cantidad);
            return existente.get();
        }
        ItemCarrito item = new ItemCarrito(producto, cantidad);
        item.asignarA(this);
        items.add(item);
        return item;
    }

    /**
     * RN-037: la línea tiene que pertenecer a este carrito.
     *
     * <p>Comprobar solo el id del ítem permitiría a cualquiera con un id válido
     * modificar el carrito de otra persona. El carrito se identifica con un UUID
     * que no se adivina, pero el id de línea es un entero secuencial: sin esta
     * comprobación, el UUID deja de proteger nada.
     */
    public Optional<ItemCarrito> buscarItem(Long idItem) {
        return items.stream().filter(item -> Objects.equals(item.getId(), idItem)).findFirst();
    }

    public void cambiarCantidad(ItemCarrito item, int cantidad) {
        item.cambiarCantidad(cantidad);
    }

    public boolean eliminarItem(Long idItem) {
        return items.removeIf(item -> Objects.equals(item.getId(), idItem));
    }

    public void vaciar() {
        items.clear();
        cupon = null;
    }

    /** Un cupón por carrito (RN-043): aplicar uno nuevo reemplaza al anterior. */
    public void aplicarCupon(Cupon cupon) {
        this.cupon = cupon;
    }

    public void quitarCupon() {
        this.cupon = null;
    }

    public void marcarConvertido() {
        this.estado = EstadoCarrito.CONVERTIDO;
    }

    public boolean estaVacio() {
        return items.isEmpty();
    }

    public int totalUnidades() {
        return items.stream().mapToInt(ItemCarrito::getCantidad).sum();
    }

    /**
     * Dos productos son el mismo si comparten id persistido.
     *
     * <p>Dos entidades sin id <strong>no</strong> son iguales aunque ambas
     * tengan id nulo: son objetos nuevos distintos. {@code Objects.equals}
     * directamente sobre los ids los haría iguales, y eso mezcla líneas.
     */
    private static boolean esElMismoProducto(Producto a, Producto b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        Long idA = a.getId();
        return idA != null && idA.equals(b.getId());
    }

    // ----- getters -----

    public UUID getId() {
        return id;
    }

    public Cupon getCupon() {
        return cupon;
    }

    public EstadoCarrito getEstado() {
        return estado;
    }

    public List<ItemCarrito> getItems() {
        return Collections.unmodifiableList(items);
    }
}
