package com.retailstore.api.cuenta.dominio;

import com.retailstore.api.cliente.dominio.Cliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Un eslabón de la cadena de refresco de la tienda.
 *
 * <p>Mismo diseño que {@code TokenRefrescoAdmin} y no la misma tabla a
 * propósito: separar las dos audiencias en el token y compartir el almacén del
 * refresco dejaría la puerta que ADR-0008 cierra. Además la vigencia es
 * distinta -30 días frente a 12 horas- porque el riesgo también lo es.
 *
 * <p>Se guarda el <strong>hash</strong> del token, nunca el token: quien lea
 * esta tabla no puede fabricar una sesión.
 */
@Entity
@Table(name = "token_refresco_cliente")
public class TokenRefrescoCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token_refresco_cliente")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_id_cliente", nullable = false)
    private Cliente cliente;

    @Column(name = "hash_token", nullable = false, unique = true, length = 64)
    private String hashToken;

    /** Agrupa todas las rotaciones de un mismo acceso. Si una se reutiliza, cae entera. */
    @Column(nullable = false)
    private UUID familia;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected TokenRefrescoCliente() {
        // requerido por JPA
    }

    public TokenRefrescoCliente(Cliente cliente, String hashToken, UUID familia,
                                Instant expiraEn, Instant creadoEn) {
        this.cliente = cliente;
        this.hashToken = hashToken;
        this.familia = familia;
        this.expiraEn = expiraEn;
        this.creadoEn = creadoEn;
    }

    // ----- reglas de dominio -----

    public boolean estaVigente(Instant ahora) {
        return usadoEn == null && revocadoEn == null && ahora.isBefore(expiraEn);
    }

    public boolean fueUsado() {
        return usadoEn != null;
    }

    public void marcarUsado(Instant momento) {
        this.usadoEn = momento;
    }

    // ----- getters -----

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getHashToken() {
        return hashToken;
    }

    public UUID getFamilia() {
        return familia;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }

    public Instant getRevocadoEn() {
        return revocadoEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
