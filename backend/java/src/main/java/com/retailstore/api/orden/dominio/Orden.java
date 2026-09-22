package com.retailstore.api.orden.dominio;

import com.retailstore.api.comun.auditoria.EntidadAuditable;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import com.retailstore.api.cliente.dominio.Cliente;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un carrito confirmado.
 *
 * <p><strong>Sus importes son inmutables</strong> (RN-053): no hay ningún
 * método que los cambie, y por eso no hay forma de que un endpoint futuro los
 * modifique por descuido. Lo único que evoluciona es el estado.
 */
@Entity
@Table(name = "orden")
public class Orden extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden")
    private Long id;

    @Column(nullable = false, unique = true, length = 24)
    private String numero;

    /**
     * Nulo si compró sin cuenta.
     *
     * <p>Los datos de contacto de abajo se copian y no se leen de aquí: la
     * orden no puede depender de que la cuenta siga existiendo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_id_cliente")
    private Cliente cliente;

    @Column(name = "nombre_contacto", nullable = false, length = 120)
    private String nombreContacto;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false, length = 300)
    private String direccion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "codigo_cupon", length = 40)
    private String codigoCupon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoOrden estado = EstadoOrden.PENDIENTE;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemOrden> items = new ArrayList<>();

    protected Orden() {
        // requerido por JPA
    }

    /**
     * Nace de un carrito confirmado.
     *
     * <p>Los importes llegan ya calculados por el servidor (RN-051) y aquí no
     * se recalculan ni se validan contra nada que venga del cliente: el
     * constructor es privado al paquete de quien confirma, y ningún DTO de
     * entrada lleva dinero.
     *
     * <p>El contacto se <strong>copia</strong> aunque haya cliente (RN-052):
     * una orden es un hecho ocurrido y tiene que seguir diciendo a dónde se
     * mandó aunque mañana esa persona cambie su dirección o borre la cuenta.
     */
    public Orden(String numero, Cliente cliente, String nombreContacto, String email,
                 String telefono, String direccion, BigDecimal subtotal,
                 BigDecimal descuento, BigDecimal total, String codigoCupon) {
        this.numero = numero;
        this.cliente = cliente;
        this.nombreContacto = nombreContacto;
        this.email = email;
        this.telefono = telefono;
        this.direccion = direccion;
        this.subtotal = subtotal;
        this.descuento = descuento;
        this.total = total;
        this.codigoCupon = codigoCupon;
    }

    // ----- reglas de dominio -----

    /**
     * Añade una línea con los datos del producto <strong>copiados</strong>
     * (RN-052). Lo hace {@code ItemOrden} en su constructor.
     *
     * <p>Solo tiene sentido mientras la orden se está creando. Después, los
     * importes son inmutables (RN-053) y añadir una línea los dejaría sin
     * cuadrar con el total ya escrito.
     */
    public void agregarLinea(ItemOrden linea) {
        if (id != null) {
            throw new IllegalStateException(
                    "No se pueden añadir líneas a una orden ya persistida: sus importes son inmutables (RN-053).");
        }
        linea.asignarA(this);
        items.add(linea);
    }

    /**
     * Cambia de estado si la transición es válida (RN-054).
     *
     * @return true si hay que devolver el stock
     */
    public boolean moverA(EstadoOrden destino) {
        if (estado == destino) {
            // Repetir el estado actual no es un error del cliente ni una
            // transición: es una petición que no cambia nada.
            return false;
        }
        if (!estado.puedeIrA(destino)) {
            throw new ExcepcionAplicacion(CodigoError.INVALID_ORDER_TRANSITION,
                    "Una orden " + estado.etiqueta().toLowerCase() + " no puede pasar a "
                            + destino.etiqueta().toLowerCase() + ".")
                    .con("estadoActual", estado.name())
                    .con("estadoSolicitado", destino.name())
                    .con("transicionesPermitidas", estado.siguientes().stream().map(Enum::name).sorted().toList());
        }
        boolean devolverStock = estado.devuelveStock(destino);
        this.estado = destino;
        return devolverStock;
    }

    public int totalUnidades() {
        return items.stream().mapToInt(ItemOrden::getCantidad).sum();
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getNombreContacto() {
        return nombreContacto;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDescuento() {
        return descuento;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCodigoCupon() {
        return codigoCupon;
    }

    public EstadoOrden getEstado() {
        return estado;
    }

    public List<ItemOrden> getItems() {
        return Collections.unmodifiableList(items);
    }
}
