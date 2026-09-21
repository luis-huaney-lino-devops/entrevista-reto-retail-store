package com.retailstore.api.cupon.dominio;

import com.retailstore.api.comun.auditoria.EntidadEliminable;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Descuento aplicable a un carrito.
 *
 * <p>Las tres condiciones -vigencia, mínimo y usos- se comprueban aquí y cada
 * una tiene su propio código de error. Un único {@code CUPON_INVALIDO} obligaría
 * al comprador a adivinar si el código caducó, si le falta importe o si se
 * agotó, y son tres acciones distintas.
 */
// Filtra lo eliminado en toda consulta HQL o de criterios, sin que haya que
// acordarse en cada repositorio. NO se aplica a find(id): ahí Hibernate va
// directo a la clave primaria, y por eso los servicios comprueban
// estaEliminado() al buscar por id.
@SQLRestriction("eliminado_en is null")
@Entity
@Table(name = "cupon")
public class Cupon extends EntidadEliminable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cupon")
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TipoCupon tipo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "subtotal_minimo", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalMinimo = BigDecimal.ZERO;

    @Column(name = "inicia_en", nullable = false)
    private Instant iniciaEn;

    @Column(name = "termina_en", nullable = false)
    private Instant terminaEn;

    @Column(name = "usos_maximos")
    private Integer usosMaximos;

    @Column(name = "usos_actuales", nullable = false)
    private int usosActuales;

    @Column(nullable = false)
    private boolean activo = true;

    protected Cupon() {
        // requerido por JPA
    }

    public Cupon(String codigo, TipoCupon tipo, BigDecimal valor, BigDecimal subtotalMinimo,
                 Instant iniciaEn, Instant terminaEn, Integer usosMaximos) {
        this.codigo = codigo;
        this.tipo = tipo;
        this.valor = valor;
        this.subtotalMinimo = subtotalMinimo;
        this.iniciaEn = iniciaEn;
        this.terminaEn = terminaEn;
        this.usosMaximos = usosMaximos;
    }

    // ----- reglas de dominio -----

    /** RN-040: activo y dentro de su ventana de vigencia. */
    public boolean estaVigente(Instant ahora) {
        return activo && !ahora.isBefore(iniciaEn) && ahora.isBefore(terminaEn);
    }

    /** RN-042: quedan usos. */
    public boolean quedanUsos() {
        return usosMaximos == null || usosActuales < usosMaximos;
    }

    /** RN-041: el subtotal alcanza el mínimo. */
    public boolean alcanzaMinimo(BigDecimal subtotal) {
        return subtotal.compareTo(subtotalMinimo) >= 0;
    }

    /**
     * Aplica o no, sin lanzar. Lo usa el carrito para decidir si descuenta:
     * un cupón que deja de aplicar se ignora pero sigue vinculado (RN-045).
     */
    public boolean aplicaA(BigDecimal subtotal, Instant ahora) {
        return estaVigente(ahora) && quedanUsos() && alcanzaMinimo(subtotal);
    }

    /** Igual que {@link #aplicaA}, pero explicando por qué no. Lo usa quien canjea. */
    public void exigirAplicable(BigDecimal subtotal, Instant ahora) {
        if (!estaVigente(ahora)) {
            throw new ExcepcionAplicacion(CodigoError.COUPON_NOT_APPLICABLE,
                    "El cupón '" + codigo + "' no está vigente.");
        }
        if (!quedanUsos()) {
            throw new ExcepcionAplicacion(CodigoError.COUPON_EXHAUSTED,
                    "El cupón '" + codigo + "' ya agotó sus usos.");
        }
        if (!alcanzaMinimo(subtotal)) {
            throw new ExcepcionAplicacion(CodigoError.COUPON_MIN_NOT_MET,
                    "El cupón '" + codigo + "' requiere una compra mínima.")
                    .con("subtotalActual", subtotal)
                    .con("subtotalMinimo", subtotalMinimo);
        }
    }

    /**
     * Descuento acotado al subtotal (RN-044).
     *
     * <p>Sin el tope, un cupón de 50 soles sobre un carrito de 30 produce un
     * total negativo: la tienda tendría que pagar al comprador.
     */
    public BigDecimal descuentoPara(BigDecimal subtotal) {
        BigDecimal bruto = tipo.descuentoSobre(subtotal, valor);
        return bruto.min(subtotal);
    }

    /** RN-042. Lo llama quien confirma la compra, dentro de la transacción. */
    public void registrarUso() {
        if (!quedanUsos()) {
            throw new ExcepcionAplicacion(CodigoError.COUPON_EXHAUSTED,
                    "El cupón '" + codigo + "' ya agotó sus usos.");
        }
        usosActuales++;
    }

    public void editar(TipoCupon tipo, BigDecimal valor, BigDecimal subtotalMinimo,
                       Instant iniciaEn, Instant terminaEn, Integer usosMaximos, boolean activo) {
        if (usosMaximos != null && usosMaximos < usosActuales) {
            // Bajar el tope por debajo de lo ya consumido dejaría el cupón en un
            // estado que la restricción de la base rechaza.
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR,
                    "El máximo de usos no puede ser menor que los " + usosActuales + " ya consumidos.");
        }
        this.tipo = tipo;
        this.valor = valor;
        this.subtotalMinimo = subtotalMinimo;
        this.iniciaEn = iniciaEn;
        this.terminaEn = terminaEn;
        this.usosMaximos = usosMaximos;
        this.activo = activo;
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public TipoCupon getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public BigDecimal getSubtotalMinimo() {
        return subtotalMinimo;
    }

    public Instant getIniciaEn() {
        return iniciaEn;
    }

    public Instant getTerminaEn() {
        return terminaEn;
    }

    public Integer getUsosMaximos() {
        return usosMaximos;
    }

    public int getUsosActuales() {
        return usosActuales;
    }

    public boolean isActivo() {
        return activo;
    }
}
