package com.retailstore.api.cuenta.dominio;

import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.comun.auditoria.EntidadEliminable;
import com.retailstore.api.ubigeo.dominio.Distrito;
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
import org.hibernate.annotations.SQLRestriction;

/**
 * Una dirección de entrega del cliente.
 *
 * <p>Se elimina de forma lógica (RN-086) y por una razón concreta además de la
 * regla: las órdenes ya enviadas se explican con la dirección a la que fueron,
 * y un borrado físico dejaría un histórico que no se puede leer.
 *
 * <p>La condición de predeterminada la protege un índice único parcial
 * ({@code uq_direccion_cliente_predeterminada}), no este código: dos peticiones
 * simultáneas para marcar direcciones distintas ganarían las dos la
 * comprobación en memoria.
 */
@SQLRestriction("eliminado_en is null")
@Entity
@Table(name = "direccion_cliente")
public class DireccionCliente extends EntidadEliminable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_direccion_cliente")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_distrito", nullable = false)
    private Distrito distrito;

    /** «Casa», «Oficina»: para que el cliente distinga las suyas de un vistazo. */
    @Column(nullable = false, length = 40)
    private String etiqueta;

    @Column(nullable = false, length = 120)
    private String destinatario;

    @Column(nullable = false, length = 20)
    private String telefono;

    @Column(nullable = false, length = 200)
    private String calle;

    @Column(length = 20)
    private String numero;

    /** Interior, piso, manzana y lote: lo que no cabe en «calle y número». */
    @Column(length = 300)
    private String referencia;

    @Column(name = "codigo_postal", length = 12)
    private String codigoPostal;

    /**
     * Coordenadas del punto marcado en el mapa. O las dos o ninguna: media
     * coordenada no ubica nada, y la base lo exige con un CHECK.
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitud;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitud;

    @Column(nullable = false)
    private boolean predeterminada;

    protected DireccionCliente() {
        // requerido por JPA
    }

    public DireccionCliente(Cliente cliente, Distrito distrito, String etiqueta, String destinatario,
                            String telefono, String calle) {
        this.cliente = cliente;
        this.distrito = distrito;
        this.etiqueta = etiqueta;
        this.destinatario = destinatario;
        this.telefono = telefono;
        this.calle = calle;
    }

    // ----- reglas de dominio -----

    public void ubicar(Distrito distrito, String calle, String numero, String referencia,
                       String codigoPostal, BigDecimal latitud, BigDecimal longitud) {
        this.distrito = distrito;
        this.calle = calle;
        this.numero = numero;
        this.referencia = referencia;
        this.codigoPostal = codigoPostal;
        // Las dos juntas: si una llega sin la otra, ninguna se guarda. Guardar
        // solo la latitud pasaría el CHECK de rango y fallaría el de pareja.
        boolean completas = latitud != null && longitud != null;
        this.latitud = completas ? latitud : null;
        this.longitud = completas ? longitud : null;
    }

    public void identificar(String etiqueta, String destinatario, String telefono) {
        this.etiqueta = etiqueta;
        this.destinatario = destinatario;
        this.telefono = telefono;
    }

    public void marcarPredeterminada() {
        this.predeterminada = true;
    }

    public void quitarPredeterminada() {
        this.predeterminada = false;
    }

    /**
     * Igualdad por identidad de fila, tolerando entidades aún sin id.
     *
     * <p>{@code getId().equals(...)} revienta con una recién creada, y
     * {@code Objects.equals} haría iguales a dos que todavía no se han
     * guardado. Es el mismo patrón de {@code Producto.esMismo}.
     */
    public boolean esMisma(DireccionCliente otra) {
        return otra != null && id != null && id.equals(otra.id);
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Distrito getDistrito() {
        return distrito;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCalle() {
        return calle;
    }

    public String getNumero() {
        return numero;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getCodigoPostal() {
        return codigoPostal;
    }

    public BigDecimal getLatitud() {
        return latitud;
    }

    public BigDecimal getLongitud() {
        return longitud;
    }

    public boolean isPredeterminada() {
        return predeterminada;
    }
}
